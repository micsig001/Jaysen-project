package com.task.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.task.entity.Task;
import com.task.mapper.TaskMapper;
import com.task.wework.WeWorkMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务超时预警定时任务
 * 每小时检查即将超时的任务并发送预警通知
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskTimeoutScheduler {

    private final TaskMapper taskMapper;
    private final WeWorkMessageService messageService;

    @Value("${wework.message.timeout-warning-minutes:60}")
    private int timeoutWarningMinutes;

    /**
     * 每小时执行一次超时检查
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void checkTimeoutTasks() {
        log.info("开始检查超时任务");

        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime warningThreshold = now.plusMinutes(timeoutWarningMinutes);

            // 查询条件：
            // 1. 状态为 IN_PROGRESS（进行中）
            // 2. 有实际截止时间
            // 3. 截止时间在预警阈值内（默认1小时内）
            // 4. 尚未超时
            LambdaQueryWrapper<Task> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Task::getStatus, "IN_PROGRESS")
                    .isNotNull(Task::getActualDeadline)
                    .le(Task::getActualDeadline, warningThreshold)
                    .gt(Task::getActualDeadline, now);

            List<Task> tasks = taskMapper.selectList(queryWrapper);

            if (tasks.isEmpty()) {
                log.info("没有即将超时的任务");
                return;
            }

            log.info("发现 {} 个即将超时的任务", tasks.size());

            // 发送预警通知
            for (Task task : tasks) {
                try {
                    messageService.notifyTimeoutWarning(task, task.getAssigneeId());
                    log.debug("已发送任务 {} 的超时预警给 {}", task.getId(), task.getAssigneeId());
                } catch (Exception e) {
                    log.error("发送任务 {} 的超时预警失败", task.getId(), e);
                }
            }

            log.info("超时任务检查完成");
        } catch (Exception e) {
            log.error("超时任务检查异常", e);
        }
    }

    /**
     * 每天凌晨检查已超时的任务（可选，用于记录或统计）
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void checkOverdueTasks() {
        log.info("开始检查已超时任务");

        try {
            LocalDateTime now = LocalDateTime.now();

            // 查询已超时但未完成的任务
            // 修复（C5）：状态值 PENDING_REVIEW → PENDING_VERIFY（与 V1 SQL 一致）
            LambdaQueryWrapper<Task> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.in(Task::getStatus, "IN_PROGRESS", "PENDING_VERIFY")
                    .isNotNull(Task::getActualDeadline)
                    .lt(Task::getActualDeadline, now);

            List<Task> overdueTasks = taskMapper.selectList(queryWrapper);

            if (overdueTasks.isEmpty()) {
                log.info("没有已超时的任务");
                return;
            }

            log.warn("发现 {} 个已超时的任务", overdueTasks.size());

            // 这里可以添加额外的处理逻辑，如：
            // 1. 发送严重超时通知给管理员
            // 2. 记录超时统计信息
            // 3. 自动升级任务优先级等

            for (Task task : overdueTasks) {
                long overdueHours = java.time.Duration.between(task.getActualDeadline(), now).toHours();
                log.warn("任务 {} 已超时 {} 小时，状态：{}", task.getId(), overdueHours, task.getStatus());
            }

            log.info("已超时任务检查完成");
        } catch (Exception e) {
            log.error("已超时任务检查异常", e);
        }
    }
}
