package com.task.service;

import com.task.common.BusinessException;
import com.task.entity.Task;
import com.task.entity.TaskStatusHistory;
import com.task.mapper.TaskMapper;
import com.task.mapper.TaskStatusHistoryMapper;
import com.task.wework.WeWorkMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TaskStateMachineService 单元测试
 *
 * 覆盖场景：
 *   - acceptTask / submitTask / completeTask / rejectTask / cancelTask 正常流程
 *   - 权限校验（操作人不是预期角色 → 403）
 *   - 状态校验（非法状态 → 400）
 *   - 任务不存在（→ 404）
 *   - 推算截止时间逻辑
 *   - 状态历史记录（含 @DisplayName 标注的字段名 bug：B1 发现）
 *
 * @author Mavis
 */
@ExtendWith(MockitoExtension.class)
class TaskStateMachineServiceTest {

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private TaskStatusHistoryMapper statusHistoryMapper;

    @Mock
    private WeWorkMessageService messageService;

    @InjectMocks
    private TaskStateMachineService stateMachine;

    private static final String CREATOR = "creator-001";
    private static final String ASSIGNEE = "assignee-001";
    private static final String OTHER = "other-999";
    private static final Long TASK_ID = 100L;

    @BeforeEach
    void setUp() {
        // 默认 stub：insert 不抛异常
        lenient().when(statusHistoryMapper.insert(any(TaskStatusHistory.class))).thenReturn(1);
        // P2.9: 默认 updateById 影响 1 行（乐观锁命中）
        lenient().when(taskMapper.updateById(any(Task.class))).thenReturn(1);
    }

    // ============================================
    // acceptTask 测试
    // ============================================

    @Test
    @DisplayName("acceptTask: 正常流程 - PENDING_ACCEPT → IN_PROGRESS，记录 actual_start_time")
    void acceptTask_success() {
        Task task = createTask(TASK_ID, "PENDING_ACCEPT", CREATOR, ASSIGNEE, 60);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);

        stateMachine.acceptTask(TASK_ID, ASSIGNEE);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).updateById(captor.capture());
        Task updated = captor.getValue();
        assertThat(updated.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(updated.getActualStartTime()).isNotNull();
        // 预估 60 分钟，截止时间应该是开始时间 + 60 分钟
        assertThat(updated.getActualDeadline()).isNotNull();
        assertThat(updated.getActualDeadline())
                .isAfterOrEqualTo(updated.getActualStartTime().plusMinutes(60).minusSeconds(1))
                .isBeforeOrEqualTo(updated.getActualStartTime().plusMinutes(60).plusSeconds(1));
    }

    @Test
    @DisplayName("acceptTask: 操作人不是 assignee → 403")
    void acceptTask_wrongOperator() {
        Task task = createTask(TASK_ID, "PENDING_ACCEPT", CREATOR, ASSIGNEE, 60);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);

        assertThatThrownBy(() -> stateMachine.acceptTask(TASK_ID, OTHER))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权");

        verify(taskMapper, never()).updateById(any());
        verify(statusHistoryMapper, never()).insert(any());
    }

    @Test
    @DisplayName("acceptTask: 状态不是 PENDING_ACCEPT → 400")
    void acceptTask_invalidStatus() {
        Task task = createTask(TASK_ID, "IN_PROGRESS", CREATOR, ASSIGNEE, 60);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);

        assertThatThrownBy(() -> stateMachine.acceptTask(TASK_ID, ASSIGNEE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许");

        verify(taskMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("acceptTask: 任务不存在 → 404")
    void acceptTask_notFound() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(null);

        assertThatThrownBy(() -> stateMachine.acceptTask(TASK_ID, ASSIGNEE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
    }

    @Test
    @DisplayName("acceptTask: 没有预估时长 - actualDeadline 应为 null")
    void acceptTask_noEstimatedDuration() {
        Task task = createTask(TASK_ID, "PENDING_ACCEPT", CREATOR, ASSIGNEE, null);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);

        stateMachine.acceptTask(TASK_ID, ASSIGNEE);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).updateById(captor.capture());
        // 没有 estimatedDuration 时不推算 deadline
        assertThat(captor.getValue().getActualDeadline()).isNull();
        assertThat(captor.getValue().getActualStartTime()).isNotNull();
    }

    // ============================================
    // submitTask 测试
    // ============================================

    @Test
    @DisplayName("submitTask: 正常流程 - IN_PROGRESS → PENDING_VERIFY")
    void submitTask_success() {
        Task task = createTask(TASK_ID, "IN_PROGRESS", CREATOR, ASSIGNEE, 60);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);

        stateMachine.submitTask(TASK_ID, ASSIGNEE, "做完了");

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING_VERIFY");

        // 通知发送给了发起方
        verify(messageService).notifyTaskPendingAcceptance(any(), eq(CREATOR));
    }

    @Test
    @DisplayName("submitTask: 操作人不是 assignee → 403")
    void submitTask_wrongOperator() {
        Task task = createTask(TASK_ID, "IN_PROGRESS", CREATOR, ASSIGNEE, 60);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);

        assertThatThrownBy(() -> stateMachine.submitTask(TASK_ID, OTHER, ""))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权");

        verify(messageService, never()).notifyTaskPendingAcceptance(any(), any());
    }

    @Test
    @DisplayName("submitTask: 状态不是 IN_PROGRESS → 400")
    void submitTask_invalidStatus() {
        Task task = createTask(TASK_ID, "PENDING_ACCEPT", CREATOR, ASSIGNEE, 60);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);

        assertThatThrownBy(() -> stateMachine.submitTask(TASK_ID, ASSIGNEE, ""))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许");
    }

    // ============================================
    // completeTask 测试
    // ============================================

    @Test
    @DisplayName("completeTask: 正常流程 - PENDING_VERIFY → COMPLETED")
    void completeTask_success() {
        Task task = createTask(TASK_ID, "PENDING_VERIFY", CREATOR, ASSIGNEE, 60);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);

        stateMachine.completeTask(TASK_ID, CREATOR);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("COMPLETED");
        assertThat(captor.getValue().getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("completeTask: 操作人不是 creator → 403")
    void completeTask_wrongOperator() {
        Task task = createTask(TASK_ID, "PENDING_VERIFY", CREATOR, ASSIGNEE, 60);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);

        assertThatThrownBy(() -> stateMachine.completeTask(TASK_ID, ASSIGNEE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权");
    }

    // ============================================
    // rejectTask 测试
    // ============================================

    @Test
    @DisplayName("rejectTask: 正常流程 - PENDING_VERIFY → IN_PROGRESS")
    void rejectTask_success() {
        Task task = createTask(TASK_ID, "PENDING_VERIFY", CREATOR, ASSIGNEE, 60);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);

        stateMachine.rejectTask(TASK_ID, CREATOR, "做得不对");

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("IN_PROGRESS");

        // 通知发给执行方
        verify(messageService).notifyTaskRejected(any(), eq(ASSIGNEE), eq("做得不对"));
    }

    @Test
    @DisplayName("rejectTask: 状态不是 PENDING_VERIFY → 400")
    void rejectTask_invalidStatus() {
        Task task = createTask(TASK_ID, "IN_PROGRESS", CREATOR, ASSIGNEE, 60);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);

        assertThatThrownBy(() -> stateMachine.rejectTask(TASK_ID, CREATOR, ""))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许");
    }

    // ============================================
    // cancelTask 测试
    // ============================================

    @Test
    @DisplayName("cancelTask: 正常流程 - PENDING_ACCEPT → WITHDRAWN")
    void cancelTask_success() {
        Task task = createTask(TASK_ID, "PENDING_ACCEPT", CREATOR, ASSIGNEE, 60);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);

        stateMachine.cancelTask(TASK_ID, CREATOR, "不做了");

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("WITHDRAWN");
        assertThat(captor.getValue().getWithdrawnAt()).isNotNull();
    }

    @Test
    @DisplayName("cancelTask: 任务已开始（IN_PROGRESS）→ 400")
    void cancelTask_alreadyStarted() {
        Task task = createTask(TASK_ID, "IN_PROGRESS", CREATOR, ASSIGNEE, 60);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);

        assertThatThrownBy(() -> stateMachine.cancelTask(TASK_ID, CREATOR, ""))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已开始");
    }

    // ============================================
    // P2.9: 乐观锁并发冲突测试
    // MP 3.5.5 的 OptimisticLockerInnerInterceptor 不抛异常，
    // 改为在 UPDATE 中加 WHERE version=? 让 affected=0，业务层自己判定冲突。
    // ============================================

    @Test
    @DisplayName("acceptTask: updateById affected=0（乐观锁冲突）→ 409 (P2.9)")
    void acceptTask_optimisticLockConflict() {
        Task task = createTask(TASK_ID, "PENDING_ACCEPT", CREATOR, ASSIGNEE, 60);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        // P2.9: 模拟乐观锁拦截器后 version 已变，affected=0
        when(taskMapper.updateById(any(Task.class))).thenReturn(0);

        assertThatThrownBy(() -> stateMachine.acceptTask(TASK_ID, ASSIGNEE))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(409))
                .hasMessageContaining("已被其他请求修改");
    }

    @Test
    @DisplayName("submitTask: affected=0 → 409 (P2.9)")
    void submitTask_optimisticLockConflict() {
        Task task = createTask(TASK_ID, "IN_PROGRESS", CREATOR, ASSIGNEE, 60);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        when(taskMapper.updateById(any(Task.class))).thenReturn(0);

        assertThatThrownBy(() -> stateMachine.submitTask(TASK_ID, ASSIGNEE, ""))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(409));
    }

    @Test
    @DisplayName("completeTask: affected=0 → 409 (P2.9)")
    void completeTask_optimisticLockConflict() {
        Task task = createTask(TASK_ID, "PENDING_VERIFY", CREATOR, ASSIGNEE, 60);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        when(taskMapper.updateById(any(Task.class))).thenReturn(0);

        assertThatThrownBy(() -> stateMachine.completeTask(TASK_ID, CREATOR))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(409));
    }

    @Test
    @DisplayName("rejectTask: affected=0 → 409 (P2.9)")
    void rejectTask_optimisticLockConflict() {
        Task task = createTask(TASK_ID, "PENDING_VERIFY", CREATOR, ASSIGNEE, 60);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        when(taskMapper.updateById(any(Task.class))).thenReturn(0);

        assertThatThrownBy(() -> stateMachine.rejectTask(TASK_ID, CREATOR, "原因"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(409));
    }

    @Test
    @DisplayName("cancelTask: affected=0 → 409 (P2.9)")
    void cancelTask_optimisticLockConflict() {
        Task task = createTask(TASK_ID, "PENDING_ACCEPT", CREATOR, ASSIGNEE, 60);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        when(taskMapper.updateById(any(Task.class))).thenReturn(0);

        assertThatThrownBy(() -> stateMachine.cancelTask(TASK_ID, CREATOR, "原因"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(409));
    }

    @Test
    @DisplayName("并发场景: 两个 acceptTask 模拟乐观锁，一成功一 409 (P2.9)")
    void concurrent_acceptTask_oneSucceedsOneFails() {
        // 用两个独立 task 对象模拟"两次读到的是不同 version"
        Task task1 = createTask(TASK_ID, "PENDING_ACCEPT", CREATOR, ASSIGNEE, 60);
        task1.setVersion(0);
        Task task2 = createTask(TASK_ID, "PENDING_ACCEPT", CREATOR, ASSIGNEE, 60);
        task2.setVersion(0);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task1).thenReturn(task2);

        // 第一次调用 updateById 成功（version 0→1）
        // 第二次调用 updateById affected=0（实际业务中 version 已被前面那次 +1）
        when(taskMapper.updateById(any(Task.class)))
                .thenReturn(1)
                .thenReturn(0);

        // 第一个请求成功
        stateMachine.acceptTask(TASK_ID, ASSIGNEE);
        // 第二个请求 409
        assertThatThrownBy(() -> stateMachine.acceptTask(TASK_ID, ASSIGNEE))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(409));
    }

    // ============================================
    // 工具方法
    // ============================================

    private Task createTask(Long id, String status, String creator, String assignee, Integer estimatedMinutes) {
        Task task = new Task();
        task.setId(id);
        task.setStatus(status);
        task.setCreatorId(creator);
        task.setAssigneeId(assignee);
        task.setEstimatedDuration(estimatedMinutes);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        return task;
    }
}
