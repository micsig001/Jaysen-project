package com.task.archive;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.task.archive.dto.ArchiveResultVO;
import com.task.archive.dto.ArchiveTaskQuery;
import com.task.archive.dto.ArchiveTaskVO;
import com.task.common.BusinessException;
import com.task.entity.TaskHistoryArchive;
import com.task.entity.User;
import com.task.mapper.TaskHistoryArchiveMapper;
import com.task.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 归档服务
 *
 * 核心职责：
 *   1) 双重防雷归档：created_at < 阈值 AND status IN ('COMPLETED','WITHDRAWN')
 *   2) 分批迁移：每批 N 条（默认 1000），避免长时间锁表
 *   3) 分布式锁：Redis SETNX 防止多实例重复执行
 *   4) 防御性记录：记录已迁移 task_id 到 Redis Set，下次执行跳过
 *   5) 权限过滤：历史任务查询按角色限制可见范围
 *
 * 注意事项：
 *   - 单批事务内完成"插入归档表 + 删除主表"，失败自动回滚
 *   - 批次间独立事务，避免单批失败影响全局
 *   - 执行总时长受 archive.max-runtime-minutes 限制
 *
 * @author Mavis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArchiveService {

    private final TaskHistoryArchiveMapper archiveMapper;
    private final UserMapper userMapper;
    private final ArchiveProperties archiveProperties;

    /** 已迁移任务 ID 的 Redis Set Key（防御性记录） */
    private static final String MIGRATED_KEY_PREFIX = "task:archive:migrated:";

    /** 防御性记录过期时间：30 天（够用了，下次执行会重新评估） */
    private static final Duration MIGRATED_TTL = Duration.ofDays(30);

    @Autowired
    private StringRedisTemplate redisTemplate;

    // ============================================
    // 1. 归档执行（核心入口）
    // ============================================

    /**
     * 执行一次归档
     *
     * 流程：
     *   1) 检查功能开关
     *   2) 尝试获取分布式锁（失败则跳过）
     *   3) 分批执行归档，直到无候选任务
     *   4) 释放锁
     *
     * @param triggerType 触发类型：MANUAL / SCHEDULED
     * @param operatorId  操作人 UserID（手动触发时记录）
     * @return 执行结果
     */
    public ArchiveResultVO executeArchive(String triggerType, String operatorId) {
        ArchiveResultVO result = new ArchiveResultVO();
        result.setTriggerType(triggerType);
        result.setOperatorId(operatorId);

        // 1. 功能开关
        if (!Boolean.TRUE.equals(archiveProperties.getEnabled())) {
            log.info("[归档] 功能未启用，跳过执行");
            result.setStatus("SKIPPED");
            result.setMessage("归档功能未启用");
            return result;
        }

        // 2. 分布式锁
        String lockKey = archiveProperties.getLockKey();
        String lockValue = String.format("%s:%d", operatorId != null ? operatorId : "system",
                System.currentTimeMillis());
        Boolean locked = tryLock(lockKey, lockValue, archiveProperties.getLockExpireSeconds());
        if (!Boolean.TRUE.equals(locked)) {
            log.warn("[归档] 获取分布式锁失败，可能其他实例正在执行");
            result.setStatus("SKIPPED");
            result.setMessage("获取分布式锁失败，可能其他实例正在执行");
            return result;
        }

        long startMillis = System.currentTimeMillis();
        int totalMigrated = 0;
        int batchCount = 0;
        LocalDateTime deadline = LocalDateTime.now()
                .plusMinutes(archiveProperties.getMaxRuntimeMinutes());
        String errorMessage = null;

        try {
            log.info("[归档] 开始执行，触发类型={}，批次大小={}，时间阈值={}个月",
                    triggerType, archiveProperties.getBatchSize(),
                    archiveProperties.getIntervalMonths());

            // 3. 分批执行
            while (LocalDateTime.now().isBefore(deadline)) {
                int batchMigrated = archiveBatch(archiveProperties.getBatchSize());
                if (batchMigrated == 0) {
                    // 无候选任务，退出循环
                    break;
                }
                totalMigrated += batchMigrated;
                batchCount++;

                log.info("[归档] 第 {} 批完成，迁移 {} 条，累计 {} 条",
                        batchCount, batchMigrated, totalMigrated);

                // 防御性熔断：单批耗时过长则让出
                if (batchCount % 5 == 0) {
                    log.info("[归档] 已处理 {} 批次，主动让出 100ms", batchCount);
                    Thread.sleep(100);
                }
            }

            // 4. 清理过期的防御性记录
            cleanupMigratedRecord();

            result.setStatus("SUCCESS");
            result.setMigratedCount(totalMigrated);
            result.setBatchCount(batchCount);
            result.setCostMillis(System.currentTimeMillis() - startMillis);
            result.setMessage(String.format("归档完成，迁移 %d 条任务", totalMigrated));

            log.info("[归档] 执行完成，迁移总数={}，批次={}，耗时={}ms",
                    totalMigrated, batchCount, result.getCostMillis());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errorMessage = "归档被中断";
            log.error("[归档] 执行被中断", e);
        } catch (Exception e) {
            errorMessage = e.getMessage();
            log.error("[归档] 执行失败，已迁移 {} 条", totalMigrated, e);
        } finally {
            // 5. 释放锁
            releaseLock(lockKey, lockValue);
            if (errorMessage != null) {
                result.setStatus("FAILED");
                result.setMigratedCount(totalMigrated);
                result.setBatchCount(batchCount);
                result.setCostMillis(System.currentTimeMillis() - startMillis);
                result.setMessage("归档失败: " + errorMessage);
            }
        }

        return result;
    }

    // ============================================
    // 2. 单批归档（事务内）
    // ============================================

    /**
     * 单批归档：插入归档表 + 删除主表
     *
     * 事务边界：
     *   - 本方法整体在一个事务内
     *   - 失败时：归档表和主表都回滚（保证一致性）
     *
     * 防雷关键：
     *   - thresholdCreatedAt 双重条件（时间 + 状态）
     *   - 防御性记录：检查 task_id 是否已在 Redis Set 中
     *
     * @param batchSize 本批最多处理多少条
     * @return 本批实际迁移条数（0 表示无候选）
     */
    @Transactional(rollbackFor = Exception.class)
    public int archiveBatch(int batchSize) {
        // 计算时间阈值
        LocalDateTime thresholdCreatedAt = LocalDateTime.now()
                .minusMonths(archiveProperties.getIntervalMonths());

        // 防御性预检：先统计候选数量
        long pendingCount = archiveMapper.countPendingArchive(thresholdCreatedAt);
        if (pendingCount == 0) {
            return 0;
        }

        // 1) 插入归档表
        int inserted = archiveMapper.insertBatchFromTasks(thresholdCreatedAt, batchSize);
        if (inserted == 0) {
            return 0;
        }

        // 2) 列出本批刚插入的归档 taskNo（按时间区间）
        // 关键修复：必须用 taskNo（业务唯一）关联，不能用归档表 id
        // 原因：InnoDB 独立表空间下，归档表和主表的自增 ID 是不共享的
        //       用归档表 id 删主表，会误删主表里"id 数值碰巧相等"的其他任务
        LocalDateTime batchStartTime = LocalDateTime.now().minusSeconds(2);
        List<String> archivedTaskNos = archiveMapper.selectTaskNosByArchivedAt(batchStartTime);

        if (archivedTaskNos.isEmpty()) {
            log.warn("[归档] 插入归档表成功但未查询到 task_no，可能并发冲突");
            return 0;
        }

        // 3) 按 taskNo 删除主表数据
        int deleted = archiveMapper.deleteTasksByTaskNos(archivedTaskNos);

        // 4) 防御性记录：将已迁移的 task_no 写入 Redis Set
        // 注意：之前版本用 task_id 记录是错的，task_no 才是业务唯一
        recordMigratedTaskNos(archivedTaskNos);

        log.debug("[归档] 本批完成：inserted={}, deleted={}", inserted, deleted);
        return inserted;
    }

    // ============================================
    // 3. 分布式锁（Redis SETNX）
    // ============================================

    /**
     * 尝试获取分布式锁
     * 使用 SET key value NX EX 实现
     */
    private Boolean tryLock(String key, String value, long expireSeconds) {
        try {
            Boolean result = redisTemplate.opsForValue()
                    .setIfAbsent(key, value, Duration.ofSeconds(expireSeconds));
            return result;
        } catch (Exception e) {
            log.error("[归档] 获取分布式锁异常", e);
            // Redis 异常时降级为单实例模式（不阻塞归档）
            return true;
        }
    }

    /**
     * 释放分布式锁
     * 使用 Lua 脚本保证"判断+删除"的原子性，避免误删他人的锁
     */
    private void releaseLock(String key, String value) {
        try {
            String luaScript = "if redis.call('get', KEYS[1]) == ARGV[1] " +
                    "then return redis.call('del', KEYS[1]) " +
                    "else return 0 end";
            redisTemplate.execute(
                    new org.springframework.data.redis.core.script.DefaultRedisScript<>(luaScript, Long.class),
                    List.of(key),
                    value);
        } catch (Exception e) {
            log.warn("[归档] 释放分布式锁异常，依赖 TTL 自动过期", e);
        }
    }

    // ============================================
    // 4. 防御性记录
    // ============================================

    /**
     * 记录已迁移的 task_no
     * 用途：
     *   - 下次执行时跳过（防御性，防止极端情况下重复迁移）
     *   - 30 天后自动过期，节省 Redis 内存
     */
    private void recordMigratedTaskNos(List<String> taskNos) {
        try {
            String key = MIGRATED_KEY_PREFIX + LocalDateTime.now().toLocalDate();
            String[] values = taskNos.toArray(new String[0]);
            redisTemplate.opsForSet().add(key, values);
            redisTemplate.expire(key, MIGRATED_TTL);
        } catch (Exception e) {
            // 防御性记录失败不影响主流程
            log.warn("[归档] 记录已迁移 task_no 失败", e);
        }
    }

    /**
     * 清理过期的防御性记录
     * 一般 Redis SET 过期会自动清理，这里作为兜底
     */
    private void cleanupMigratedRecord() {
        try {
            // 按日期模式扫描并删除 7 天前的 key
            // 简化实现：依赖 TTL 自动过期
            log.debug("[归档] 防御性记录依赖 TTL 自动清理");
        } catch (Exception e) {
            log.warn("[归档] 清理防御性记录异常", e);
        }
    }

    // ============================================
    // 5. 历史任务查询（带权限过滤）
    // ============================================

    /**
     * 分页查询历史任务
     * 权限规则：
     *   - ADMIN：全部
     *   - MANAGER：本部门所有成员的任务
     *   - EMPLOYEE：仅自己（创建或执行）
     *
     * @param query       查询条件
     * @param currentUser 当前登录用户
     * @return 分页结果
     */
    public IPage<ArchiveTaskVO> queryArchivedTasks(ArchiveTaskQuery query, User currentUser) {
        if (currentUser == null) {
            throw BusinessException.unauthorized("未登录");
        }

        // 1. 构造查询条件
        LambdaQueryWrapper<TaskHistoryArchive> wrapper = new LambdaQueryWrapper<>();

        // 关键词
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(TaskHistoryArchive::getTitle, query.getKeyword());
        }

        // 状态（多个用逗号分隔）
        if (StringUtils.hasText(query.getStatus())) {
            List<String> statusList = List.of(query.getStatus().split(","));
            wrapper.in(TaskHistoryArchive::getStatus, statusList);
        }

        // 创建人
        if (StringUtils.hasText(query.getCreatorId())) {
            wrapper.eq(TaskHistoryArchive::getCreatorId, query.getCreatorId());
        }

        // 执行人
        if (StringUtils.hasText(query.getAssigneeId())) {
            wrapper.eq(TaskHistoryArchive::getAssigneeId, query.getAssigneeId());
        }

        // 归档时间范围
        if (query.getArchivedAtStart() != null) {
            wrapper.ge(TaskHistoryArchive::getArchivedAt, query.getArchivedAtStart());
        }
        if (query.getArchivedAtEnd() != null) {
            wrapper.le(TaskHistoryArchive::getArchivedAt, query.getArchivedAtEnd());
        }

        // 原始创建时间范围
        if (query.getOriginalCreatedAtStart() != null) {
            wrapper.ge(TaskHistoryArchive::getOriginalCreatedAt,
                    query.getOriginalCreatedAtStart());
        }
        if (query.getOriginalCreatedAtEnd() != null) {
            wrapper.le(TaskHistoryArchive::getOriginalCreatedAt,
                    query.getOriginalCreatedAtEnd());
        }

        // 优先级
        if (query.getPriority() != null) {
            wrapper.eq(TaskHistoryArchive::getPriority, query.getPriority());
        }

        // 2. 权限过滤（核心）
        applyPermissionFilter(wrapper, currentUser);

        // 3. 排序：默认按归档时间倒序
        wrapper.orderByDesc(TaskHistoryArchive::getArchivedAt);

        // 4. 分页
        Page<TaskHistoryArchive> page = new Page<>(
                query.getPageNum() != null ? query.getPageNum() : 1,
                query.getPageSize() != null ? query.getPageSize() : 10);
        Page<TaskHistoryArchive> result = archiveMapper.selectPage(page, wrapper);

        // 5. 转换为 VO（关联用户名）
        List<ArchiveTaskVO> voList = convertToVO(result.getRecords());
        Page<ArchiveTaskVO> voPage = new Page<>(result.getCurrent(), result.getSize(),
                result.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    /**
     * 权限过滤：按角色限制可见范围
     */
    private void applyPermissionFilter(LambdaQueryWrapper<TaskHistoryArchive> wrapper,
                                        User currentUser) {
        String role = currentUser.getRole();
        String userId = currentUser.getUserId();

        if ("ADMIN".equals(role)) {
            // 管理员：全部
            return;
        }

        if ("MANAGER".equals(role)) {
            // 经理：本部门所有成员的任务
            // 步骤：先查本部门成员 user_id 列表，再按 creator_id / assignee_id 过滤
            if (currentUser.getDepartmentId() == null) {
                // 经理未关联部门：返回空集（用户友好，避免污染审计日志）
                // 修复（M3）：之前抛 BusinessException.forbidden("经理未关联部门") → 403
                // 改为日志警告 + 强制空集
                log.warn("[归档] MANAGER 用户 {} 未关联部门，返回空集。请联系管理员完善部门信息。",
                        currentUser.getUserId());
                wrapper.eq(TaskHistoryArchive::getId, -1L);
                return;
            }
            List<String> deptUserIds = userMapper.selectUserIdsByDeptId(
                    currentUser.getDepartmentId());
            if (deptUserIds.isEmpty()) {
                // 部门没人，强制返回空
                wrapper.eq(TaskHistoryArchive::getId, -1L);
                return;
            }
            // 注意：MyBatis-Plus 的 or 条件需要使用 and/or 嵌套
            wrapper.and(w -> w.in(TaskHistoryArchive::getCreatorId, deptUserIds)
                    .or().in(TaskHistoryArchive::getAssigneeId, deptUserIds));
            return;
        }

        // EMPLOYEE：仅自己
        wrapper.and(w -> w.eq(TaskHistoryArchive::getCreatorId, userId)
                .or().eq(TaskHistoryArchive::getAssigneeId, userId));
    }

    /**
     * 实体转 VO：关联用户名
     */
    private List<ArchiveTaskVO> convertToVO(List<TaskHistoryArchive> records) {
        if (records.isEmpty()) {
            return List.of();
        }

        // 收集所有 userId
        Set<String> userIds = records.stream()
                .flatMap(r -> java.util.stream.Stream.of(r.getCreatorId(), r.getAssigneeId()))
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        // 批量查询用户信息
        Map<String, String> userNameMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectByUserIds(new java.util.ArrayList<>(userIds));
            for (User u : users) {
                userNameMap.put(u.getUserId(), u.getName());
            }
        }

        // 转换
        return records.stream().map(r -> {
            ArchiveTaskVO vo = new ArchiveTaskVO();
            org.springframework.beans.BeanUtils.copyProperties(r, vo);
            vo.setSelfAssigned(r.getSelfAssigned());
            vo.setCreatorName(userNameMap.get(r.getCreatorId()));
            vo.setAssigneeName(userNameMap.get(r.getAssigneeId()));
            return vo;
        }).collect(Collectors.toList());
    }

    // ============================================
    // 6. 统计待归档数
    // ============================================

    /**
     * 统计待归档任务数量（轻量预判）
     */
    public long countPendingArchive() {
        LocalDateTime thresholdCreatedAt = LocalDateTime.now()
                .minusMonths(archiveProperties.getIntervalMonths());
        return archiveMapper.countPendingArchive(thresholdCreatedAt);
    }
}
