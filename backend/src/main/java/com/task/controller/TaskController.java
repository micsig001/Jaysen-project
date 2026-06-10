package com.task.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.task.audit.AuditLog;
import com.task.common.Result;
import com.task.entity.Task;
import com.task.idempotency.Idempotent;
import com.task.service.TaskService;
import com.task.service.TaskStateMachineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.task.entity.User;
import com.task.mapper.UserMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 任务控制器
 *
 * 架构说明：
 *   - CRUD 操作走 TaskService
 *   - 状态流转走 TaskStateMachineService
 *   - Controller 只做参数传递和响应包装
 *   - 写操作自动加 @Idempotent（幂等性）和 @AuditLog（审计）
 *
 * @author Mavis
 */
@Slf4j
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@Tag(name = "任务管理", description = "任务的 CRUD 接口 + 状态机操作")
public class TaskController {

    private final TaskService taskService;
    private final TaskStateMachineService stateMachineService;
    private final UserMapper userMapper;

    /**
     * 获取任务列表（支持筛选和分页 + 数据权限过滤）
     */
    @GetMapping
    @Operation(summary = "获取任务列表", description = "支持按状态、优先级、创建人、执行人筛选。数据范围按当前用户角色自动过滤")
    public Result<Map<String, Object>> getTaskList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer priority,
            @RequestParam(required = false) String creatorId,
            @RequestParam(required = false) String assigneeId
    ) {
        Page<Task> result = taskService.listTasks(pageNum, pageSize, status, priority,
                creatorId, assigneeId, currentUser());

        Map<String, Object> response = new HashMap<>();
        response.put("list", result.getRecords());
        response.put("total", result.getTotal());
        response.put("pageNum", result.getCurrent());
        response.put("pageSize", result.getSize());

        return Result.success(response);
    }

    /**
     * 获取任务详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取任务详情")
    public Result<Task> getTaskDetail(@PathVariable Long id) {
        return Result.success(taskService.getTaskById(id, currentUser()));
    }

    /**
     * 创建任务（幂等性 + 审计）
     */
    @PostMapping
    @Idempotent(operationType = "CREATE_TASK", ttlSeconds = 86400)
    @AuditLog(operationType = "CREATE", resourceType = "TASK", description = "创建任务")
    @Operation(summary = "创建任务", description = "需要 X-Idempotency-Key 请求头（UUID v4），24h 内重复请求自动返回首次结果")
    public Result<Task> createTask(@Valid @RequestBody Task task) {
        return Result.success(taskService.createTask(task));
    }

    /**
     * 更新任务
     */
    @PutMapping("/{id}")
    @AuditLog(operationType = "UPDATE", resourceType = "TASK", description = "更新任务")
    @Operation(summary = "更新任务", description = "仅 PENDING_ACCEPT 状态可编辑，且只能修改自己创建的任务")
    public Result<Task> updateTask(@PathVariable Long id, @Valid @RequestBody Task task) {
        return Result.success(taskService.updateTask(id, task, currentUser()));
    }

    /**
     * 删除任务（仅自己发给自己）
     */
    @DeleteMapping("/{id}")
    @AuditLog(operationType = "DELETE", resourceType = "TASK", description = "删除任务")
    @Operation(summary = "删除任务", description = "仅自己发给自己的任务可删除")
    public Result<Void> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id, currentUserId());
        return Result.success();
    }

    /**
     * 确认接收任务
     */
    @PostMapping("/{id}/accept")
    @AuditLog(operationType = "ACCEPT", resourceType = "TASK", description = "确认接收任务")
    @Operation(summary = "确认接收任务", description = "PENDING_ACCEPT → IN_PROGRESS，仅接收方可操作")
    public Result<Void> acceptTask(@PathVariable Long id) {
        stateMachineService.acceptTask(id, currentUserId());
        return Result.success();
    }

    /**
     * 提交完成
     */
    @PostMapping("/{id}/submit")
    @AuditLog(operationType = "SUBMIT", resourceType = "TASK", description = "提交完成")
    @Operation(summary = "提交完成", description = "IN_PROGRESS → PENDING_VERIFY，仅执行方可操作")
    public Result<Void> submitTask(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String remark = body.getOrDefault("remark", "");
        stateMachineService.submitTask(id, currentUserId(), remark);
        return Result.success();
    }

    /**
     * 验收完成
     */
    @PostMapping("/{id}/complete")
    @AuditLog(operationType = "COMPLETE", resourceType = "TASK", description = "验收完成")
    @Operation(summary = "验收完成", description = "PENDING_VERIFY → COMPLETED，仅发起方可操作")
    public Result<Void> completeTask(@PathVariable Long id) {
        stateMachineService.completeTask(id, currentUserId());
        return Result.success();
    }

    /**
     * 驳回重做
     */
    @PostMapping("/{id}/reject")
    @AuditLog(operationType = "REJECT", resourceType = "TASK", description = "驳回任务")
    @Operation(summary = "驳回重做", description = "PENDING_VERIFY → IN_PROGRESS，仅发起方可操作")
    public Result<Void> rejectTask(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String reason = body.getOrDefault("reason", "");
        stateMachineService.rejectTask(id, currentUserId(), reason);
        return Result.success();
    }

    /**
     * 取消任务
     */
    @PostMapping("/{id}/cancel")
    @AuditLog(operationType = "CANCEL", resourceType = "TASK", description = "取消任务")
    @Operation(summary = "取消任务", description = "PENDING_ACCEPT → WITHDRAWN，仅发起方可操作")
    public Result<Void> cancelTask(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String reason = body.getOrDefault("reason", "");
        stateMachineService.cancelTask(id, currentUserId(), reason);
        return Result.success();
    }

    private String currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal != null ? principal.toString() : null;
    }

    private User currentUser() {
        String userId = currentUserId();
        if (userId == null) {
            throw com.task.common.BusinessException.unauthorized("未登录");
        }
        User user = userMapper.selectByUserId(userId);
        if (user == null) {
            throw com.task.common.BusinessException.unauthorized("用户不存在: " + userId);
        }
        return user;
    }
}
