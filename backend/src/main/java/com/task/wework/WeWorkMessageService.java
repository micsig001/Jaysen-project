package com.task.wework;

import com.task.entity.Task;
import com.task.util.Labels;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 企业微信消息推送服务
 * 在三个强制节点触发推送：任务分派、驳回/待验收、超时预警
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeWorkMessageService {

    private final WeWorkApiClient weWorkApiClient;

    @Value("${wework.message.enabled:true}")
    private boolean messageEnabled;

    @Value("${wework.message.retry-times:3}")
    private int retryTimes;

    @Value("${wework.message.timeout-warning-minutes:60}")
    private int timeoutWarningMinutes;

    /**
     * 任务分派通知（发送给接收方）
     *
     * @param task 任务对象
     * @param receiverUserId 接收方企微 UserID
     */
    @Async  // 修复（P1-5）：业务调用方不等企微响应，避免阻塞主流程
    public void notifyTaskAssigned(Task task, String receiverUserId) {
        if (!messageEnabled) {
            log.debug("消息推送已禁用，跳过任务分派通知");
            return;
        }

        String title = "新任务分配";
        String description = String.format(
                "任务名称：%s\n优先级：%s\n截止时间：%s\n请及时处理",
                task.getTitle(),
                Labels.getPriorityLabel(task.getPriority()),
                task.getActualDeadline() != null ? task.getActualDeadline().toString() : "待确认"
        );

        sendMessageWithRetry(receiverUserId, title, description, "/task/detail/" + task.getId());
    }

    /**
     * 任务驳回通知（发送给发起方）
     *
     * @param task 任务对象
     * @param creatorUserId 发起方企微 UserID
     * @param rejectReason 驳回原因
     */
    @Async
    public void notifyTaskRejected(Task task, String creatorUserId, String rejectReason) {
        if (!messageEnabled) {
            log.debug("消息推送已禁用，跳过驳回通知");
            return;
        }

        String title = "任务被驳回";
        String description = String.format(
                "任务名称：%s\n驳回原因：%s\n请修改后重新提交",
                task.getTitle(),
                rejectReason != null ? rejectReason : "未填写"
        );

        sendMessageWithRetry(creatorUserId, title, description, "/task/detail/" + task.getId());
    }

    /**
     * 待验收通知（发送给发起方）
     *
     * @param task 任务对象
     * @param creatorUserId 发起方企微 UserID
     */
    @Async
    public void notifyTaskPendingAcceptance(Task task, String creatorUserId) {
        if (!messageEnabled) {
            log.debug("消息推送已禁用，跳过待验收通知");
            return;
        }

        String title = "任务待验收";
        String description = String.format(
                "任务名称：%s\n执行方已提交，请及时验收",
                task.getTitle()
        );

        sendMessageWithRetry(creatorUserId, title, description, "/task/detail/" + task.getId());
    }

    /**
     * 超时预警通知（发送给执行方）
     *
     * @param task 任务对象
     * @param executorUserId 执行方企微 UserID
     */
    @Async
    public void notifyTimeoutWarning(Task task, String executorUserId) {
        if (!messageEnabled) {
            log.debug("消息推送已禁用，跳过超时预警");
            return;
        }

        String title = "任务即将超时";
        String description = String.format(
                "任务名称：%s\n预计剩余时间：%d 分钟\n请尽快完成",
                task.getTitle(),
                timeoutWarningMinutes
        );

        sendMessageWithRetry(executorUserId, title, description, "/task/detail/" + task.getId());
    }

    /**
     * 带重试的消息发送
     */
    private void sendMessageWithRetry(String toUser, String title, String description, String url) {
        for (int i = 1; i <= retryTimes; i++) {
            try {
                Map<String, Object> message = weWorkApiClient.buildCardMessage(
                        toUser, title, description, url, "查看详情"
                );

                boolean success = weWorkApiClient.sendMessage(message);
                if (success) {
                    log.info("消息推送成功: toUser={}, title={}", toUser, title);
                    return;
                }

                // 发送失败，等待后重试
                if (i < retryTimes) {
                    long waitTime = (long) Math.pow(2, i) * 1000; // 指数退避
                    log.warn("消息推送失败，{}ms 后重试 ({}/{})", waitTime, i, retryTimes);
                    Thread.sleep(waitTime);
                }
            } catch (Exception e) {
                log.error("消息推送异常，第 {} 次重试", i, e);
                if (i < retryTimes) {
                    try {
                        long waitTime = (long) Math.pow(2, i) * 1000;
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("消息推送最终失败，已达到最大重试次数: toUser={}, title={}", toUser, title);
    }
}
