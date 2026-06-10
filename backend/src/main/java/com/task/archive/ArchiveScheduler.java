package com.task.archive;

import com.task.archive.dto.ArchiveResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 归档定时任务
 *
 * 默认每月1号凌晨3点执行（cron 可配置）
 * 执行流程详见 ArchiveService.executeArchive
 *
 * 防护机制：
 *   1) 分布式锁防止多实例重复执行
 *   2) 单次最长执行时间由 archive.max-runtime-minutes 限制
 *   3) 异常时不影响下次定时执行
 *
 * @author Mavis
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArchiveScheduler {

    private final ArchiveService archiveService;
    private final ArchiveProperties archiveProperties;

    /**
     * 定时归档
     * Cron 表达式可配置（默认每月1号凌晨3点）
     */
    @Scheduled(cron = "${archive.cron:0 0 3 1 * ?}")
    public void scheduledArchive() {
        // 功能开关检查
        if (!Boolean.TRUE.equals(archiveProperties.getEnabled())) {
            log.info("[归档定时] 功能未启用，跳过执行");
            return;
        }

        log.info("[归档定时] 开始执行");
        try {
            ArchiveResultVO result = archiveService.executeArchive("SCHEDULED", "system");
            log.info("[归档定时] 执行完成，状态={}，迁移={}条，批次={}，耗时={}ms",
                    result.getStatus(), result.getMigratedCount(),
                    result.getBatchCount(), result.getCostMillis());
        } catch (Exception e) {
            log.error("[归档定时] 执行异常", e);
        }
    }
}
