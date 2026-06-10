package com.task.idempotency;

import com.task.mapper.IdempotencyKeyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 幂等性记录清理调度器
 *
 * <p>每天凌晨 3:30 清理已过期的 idempotency_keys 记录，避免表无限增长。
 *
 * <p>cron 可通过环境变量 {@code IDEMPOTENCY_CLEANUP_CRON} 覆盖，
 * 默认 {@code "0 30 3 * * ?"}。
 *
 * @author Mavis
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyCleanupScheduler {

    private final IdempotencyKeyMapper idempotencyKeyMapper;

    /**
     * 清理过期幂等性记录
     *
     * <p>调用 {@link IdempotencyKeyMapper#deleteExpired}（DELETE WHERE expires_at &lt; now）。
     * Redis 中的 Key 由各自的 TTL 自然过期，不需要清理。
     */
    @Scheduled(cron = "${idempotency.cleanup-cron:0 30 3 * * ?}")
    public void cleanupExpiredKeys() {
        long start = System.currentTimeMillis();
        try {
            int deleted = idempotencyKeyMapper.deleteExpired(LocalDateTime.now());
            long cost = System.currentTimeMillis() - start;
            log.info("[幂等清理] 删除过期记录: deleted={}, cost={}ms", deleted, cost);
        } catch (Exception e) {
            log.error("[幂等清理] 清理失败", e);
        }
    }
}
