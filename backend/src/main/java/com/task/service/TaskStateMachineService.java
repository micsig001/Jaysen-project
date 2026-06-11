package com.task.service;

import com.task.common.BusinessException;
import com.task.entity.Task;
import com.task.entity.TaskStatusHistory;
import com.task.mapper.TaskMapper;
import com.task.mapper.TaskStatusHistoryMapper;
import com.task.wework.WeWorkMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 任务状态机服务
 * 实现双重确认状态机：PENDING_ACCEPT -> IN_PROGRESS -> PENDING_VERIFY -> COMPLETED
 *
 * <p>P2.9: 5 个状态流转方法（acceptTask/submitTask/completeTask/rejectTask/cancelTask）
 * 启用 MyBatis-Plus 乐观锁（{@code Task.@Version}）。并发更新时后者 updateById 影响行数=0，
 * 本服务统一转为 409 Conflict BusinessException。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskStateMachineService {

    private final TaskMapper taskMapper;
    private final TaskStatusHistoryMapper statusHistoryMapper;
    private final WeWorkMessageService messageService;

    /**
     * 确认接收任务（接收方操作）
     * 触发点：记录 actual_start_time，推算 actual_deadline
     */
    @Transactional(rollbackFor = Exception.class)
    public void acceptTask(Long taskId, String operatorId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }

        // 验证操作权限
        if (!Objects.equals(task.getAssigneeId(), operatorId)) {
            throw new BusinessException(403, "无权操作此任务");
        }

        // 验证状态
        if (!"PENDING_ACCEPT".equals(task.getStatus())) {
            throw new BusinessException(400, "任务状态不允许接收");
        }

        // 记录开始时间并推算截止时间
        LocalDateTime now = LocalDateTime.now();
        task.setActualStartTime(now);

        if (task.getEstimatedDuration() != null && task.getEstimatedDuration() > 0) {
            task.setActualDeadline(now.plusMinutes(task.getEstimatedDuration()));
        }

        // 更新状态（P2.9: updateById 自动带乐观锁 WHERE version=?）
        String fromStatus = task.getStatus();
        task.setStatus("IN_PROGRESS");
        task.setUpdatedAt(now);
        updateWithOptimisticLock(task);

        // 记录状态历史
        recordStatusHistory(taskId, fromStatus, "IN_PROGRESS", operatorId, "确认接收任务");

        log.info("任务 {} 已被 {} 接收", taskId, operatorId);
    }

    /**
     * 提交任务（执行方操作）
     * 进入待验收状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitTask(Long taskId, String operatorId, String submitRemark) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }

        // 验证操作权限
        if (!Objects.equals(task.getAssigneeId(), operatorId)) {
            throw new BusinessException(403, "无权操作此任务");
        }

        // 验证状态
        if (!"IN_PROGRESS".equals(task.getStatus())) {
            throw new BusinessException(400, "任务状态不允许提交");
        }

        // 更新状态（P2.9: updateById 自动带乐观锁 WHERE version=?）
        String fromStatus = task.getStatus();
        task.setStatus("PENDING_VERIFY");
        task.setUpdatedAt(LocalDateTime.now());
        updateWithOptimisticLock(task);

        // 记录状态历史
        recordStatusHistory(taskId, fromStatus, "PENDING_VERIFY", operatorId, submitRemark);

        // 发送待验收通知给发起方
        messageService.notifyTaskPendingAcceptance(task, task.getCreatorId());

        log.info("任务 {} 已提交待验收", taskId);
    }

    /**
     * 验收任务（发起方操作）
     * 任务完成
     */
    @Transactional(rollbackFor = Exception.class)
    public void completeTask(Long taskId, String operatorId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }

        // 验证操作权限
        if (!Objects.equals(task.getCreatorId(), operatorId)) {
            throw new BusinessException(403, "无权操作此任务");
        }

        // 验证状态
        if (!"PENDING_VERIFY".equals(task.getStatus())) {
            throw new BusinessException(400, "任务状态不允许验收");
        }

        // 更新状态（P2.9: updateById 自动带乐观锁 WHERE version=?）
        String fromStatus = task.getStatus();
        task.setStatus("COMPLETED");
        task.setCompletedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        updateWithOptimisticLock(task);

        // 记录状态历史
        recordStatusHistory(taskId, fromStatus, "COMPLETED", operatorId, "验收通过");

        log.info("任务 {} 已验收完成", taskId);
    }

    /**
     * 驳回任务（发起方操作）
     * 退回到 IN_PROGRESS 状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void rejectTask(Long taskId, String operatorId, String rejectReason) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }

        // 验证操作权限
        if (!Objects.equals(task.getCreatorId(), operatorId)) {
            throw new BusinessException(403, "无权操作此任务");
        }

        // 验证状态
        if (!"PENDING_VERIFY".equals(task.getStatus())) {
            throw new BusinessException(400, "任务状态不允许驳回");
        }

        // 更新状态（P2.9: updateById 自动带乐观锁 WHERE version=?）
        String fromStatus = task.getStatus();
        task.setStatus("IN_PROGRESS");
        task.setUpdatedAt(LocalDateTime.now());
        updateWithOptimisticLock(task);

        // 记录状态历史
        recordStatusHistory(taskId, fromStatus, "IN_PROGRESS", operatorId, "驳回：" + rejectReason);

        // 发送驳回通知给执行方
        messageService.notifyTaskRejected(task, task.getAssigneeId(), rejectReason);

        log.info("任务 {} 已被驳回，原因：{}", taskId, rejectReason);
    }

    /**
     * 取消任务（发起方操作）
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelTask(Long taskId, String operatorId, String cancelReason) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }

        // 验证操作权限
        if (!Objects.equals(task.getCreatorId(), operatorId)) {
            throw new BusinessException(403, "无权操作此任务");
        }

        // 验证状态（只有未接收的任务可以取消）
        if (!"PENDING_ACCEPT".equals(task.getStatus())) {
            throw new BusinessException(400, "任务已开始，无法取消");
        }

        // 更新状态（P2.9: updateById 自动带乐观锁 WHERE version=?）
        String fromStatus = task.getStatus();
        task.setStatus("WITHDRAWN");
        task.setWithdrawnAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        updateWithOptimisticLock(task);

        // 记录状态历史
        recordStatusHistory(taskId, fromStatus, "WITHDRAWN", operatorId, "取消：" + cancelReason);

        log.info("任务 {} 已取消", taskId);
    }

    /**
     * P2.9: 带乐观锁的 updateById
     *
     * <p>MyBatis-Plus 的 {@code OptimisticLockerInnerInterceptor}（在 MybatisPlusConfig 注册）
     * 会自动将 SQL 改为：
     * <pre>UPDATE ... SET version=version+1, ... WHERE id=? AND version=?</pre>
     * 并把实体 version 字段值 +1。如果影响行数=0（说明 version 已被其他请求修改），
     * 本方法手动抛 409 Conflict BusinessException。</p>
     *
     * <p>注：MP 3.5.5 的拦截器本身不抛异常，依赖业务层根据 affected=0 判定冲突。</p>
     */
    private void updateWithOptimisticLock(Task task) {
        int affected = taskMapper.updateById(task);
        if (affected == 0) {
            log.warn("[Task {}] 乐观锁冲突：affected=0，version 已被其他请求修改", task.getId());
            throw new BusinessException(409, "任务已被其他请求修改，请刷新后重试");
        }
    }

    /**
     * 记录状态历史
     */
    private void recordStatusHistory(Long taskId, String fromStatus, String toStatus,
                                      String operatorId, String remark) {
        TaskStatusHistory history = new TaskStatusHistory();
        history.setTaskId(taskId);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setOperatorId(operatorId);
        history.setRemark(remark);
        history.setCreatedAt(LocalDateTime.now());
        statusHistoryMapper.insert(history);
    }
}
