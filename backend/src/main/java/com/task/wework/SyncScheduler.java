package com.task.wework;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 企业微信数据同步定时任务
 * 每天凌晨2点执行全量同步，确保数据一致性
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncScheduler {

    private final WeWorkSyncService weWorkSyncService;

    /**
     * 每天凌晨2点执行全量同步
     * Cron 表达式：秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledFullSync() {
        log.info("触发定时全量同步任务");
        try {
            weWorkSyncService.fullSync();
            log.info("定时全量同步任务执行成功");
        } catch (Exception e) {
            log.error("定时全量同步任务执行失败", e);
        }
    }

    /**
     * 每小时执行一次增量同步（可选）
     * 仅同步用户信息，不同步部门结构
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void scheduledIncrementalSync() {
        log.info("触发定时增量同步任务");
        try {
            // 同步根部门及所有子部门成员
            weWorkSyncService.syncUsers(0L, true);
            log.info("定时增量同步任务执行成功");
        } catch (Exception e) {
            log.error("定时增量同步任务执行失败", e);
        }
    }
}
