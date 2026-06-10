package com.task.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.task.common.BusinessException;
import com.task.entity.IdempotencyKey;
import com.task.mapper.IdempotencyKeyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 幂等性服务
 *
 * 封装 Redis 和数据库的幂等性检查逻辑
 * 提供 4 个核心方法：
 *   1) tryReserve       - 尝试预留 Key（SETNX）
 *   2) getCachedResult  - 获取缓存结果
 *   3) saveResult       - 保存执行结果
 *   4) release          - 释放 Key（异常时调用）
 *
 * @author Mavis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    /** Redis Key 前缀 */
    private static final String REDIS_KEY_PREFIX = "idempotency:key:";

    /** UUID v4 格式校验正则 */
    private static final java.util.regex.Pattern UUID_PATTERN =
            java.util.regex.Pattern.compile(
                    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final StringRedisTemplate redisTemplate;
    private final IdempotencyKeyMapper idempotencyKeyMapper;
    private final ObjectMapper objectMapper;

    /**
     * 尝试预留幂等性 Key
     * 双重检查：先 Redis SETNX（快路径），再数据库 UNIQUE 约束（兜底）
     *
     * @return true=首次请求（可执行业务），false=重复请求（应返回缓存）
     */
    public boolean tryReserve(String idempotencyKey, String operationType, long ttlSeconds) {
        String redisKey = REDIS_KEY_PREFIX + idempotencyKey;
        Boolean reserved = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, "PROCESSING", Duration.ofSeconds(ttlSeconds));
        if (Boolean.TRUE.equals(reserved)) {
            return true;
        }

        // Redis 路径失败或 Key 已存在，尝试数据库兜底
        log.debug("[幂等] Redis 路径已存在 Key，尝试数据库兜底: {}", idempotencyKey);
        return tryReserveByDatabase(idempotencyKey, operationType, ttlSeconds);
    }

    /**
     * 数据库兜底：通过 UNIQUE 约束判断是否已存在
     * 注意：这是一个同步阻塞操作，仅在 Redis 不可用或 Key 已存在时使用
     */
    private boolean tryReserveByDatabase(String idempotencyKey, String operationType, long ttlSeconds) {
        // 查询是否已有持久化记录
        IdempotencyKey existing = idempotencyKeyMapper.selectByKey(idempotencyKey);
        if (existing != null) {
            log.info("[幂等] 数据库兜底检测到已存在 Key: {}", idempotencyKey);
            return false;
        }

        // 尝试插入新记录（依赖 UNIQUE 约束）
        try {
            IdempotencyKey record = new IdempotencyKey();
            record.setIdempotencyKey(idempotencyKey);
            record.setOperationType(operationType);
            record.setCreatedAt(LocalDateTime.now());
            record.setExpiresAt(LocalDateTime.now().plusSeconds(ttlSeconds));
            idempotencyKeyMapper.insert(record);
            return true;
        } catch (Exception e) {
            // 唯一键冲突 = 已存在
            log.info("[幂等] 数据库插入冲突，Key 已存在: {}", idempotencyKey);
            return false;
        }
    }

    /**
     * 获取缓存的响应数据
     * @return Optional 包装的 JSON 字符串；空表示未缓存
     */
    public Optional<String> getCachedResult(String idempotencyKey) {
        String redisKey = REDIS_KEY_PREFIX + idempotencyKey;
        String value = redisTemplate.opsForValue().get(redisKey);
        if (value == null || "PROCESSING".equals(value)) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    /**
     * 保存执行结果到 Redis 和数据库
     */
    public void saveResult(String idempotencyKey, Object result, long ttlSeconds) {
        String redisKey = REDIS_KEY_PREFIX + idempotencyKey;
        try {
            String json = objectMapper.writeValueAsString(result);
            // Redis：缓存响应结果
            redisTemplate.opsForValue().set(redisKey, json, Duration.ofSeconds(ttlSeconds));
            // 数据库：更新 response_data 字段
            idempotencyKeyMapper.updateResponseData(idempotencyKey, json,
                    LocalDateTime.now().plusSeconds(ttlSeconds));
        } catch (JsonProcessingException e) {
            log.error("[幂等] 序列化响应结果失败: {}", e.getMessage());
        }
    }

    /**
     * 释放 Key（异常时调用，允许客户端重试）
     * <p>同时清理 Redis 缓存和数据库记录（让客户端可以立即重试）
     */
    public void release(String idempotencyKey) {
        String redisKey = REDIS_KEY_PREFIX + idempotencyKey;
        try {
            redisTemplate.delete(redisKey);
        } catch (Exception e) {
            log.warn("[幂等] 释放 Redis Key 失败，依赖 TTL 自动过期: {}", e.getMessage());
        }
        // 修复（P1-13）：同步删除数据库记录（之前依赖 IdempotencyCleanupScheduler 自然过期）
        try {
            idempotencyKeyMapper.deleteByKey(idempotencyKey);
        } catch (Exception e) {
            log.warn("[幂等] 释放数据库 Key 失败: {}", e.getMessage());
        }
    }

    /**
     * 获取 X-Idempotency-Key 请求头
     * 修复（m10）：增加 UUID v4 格式校验
     * @return Key 字符串；空表示未提供
     */
    public String extractKey(jakarta.servlet.http.HttpServletRequest request) {
        String key = request.getHeader("X-Idempotency-Key");
        if (key == null || key.isBlank()) {
            throw BusinessException.badRequest("缺少幂等性 Key: X-Idempotency-Key 请求头");
        }
        if (key.length() < 8 || key.length() > 64) {
            throw BusinessException.badRequest("X-Idempotency-Key 长度必须在 8-64 之间");
        }
        // 修复（m10）：如果是 UUID 格式，必须符合 v4
        if (key.length() == 36 && !UUID_PATTERN.matcher(key).matches()) {
            throw BusinessException.badRequest("X-Idempotency-Key 格式不合法（UUID v4 必须是 8-4-4-4-12 十六进制）");
        }
        return key;
    }
}
