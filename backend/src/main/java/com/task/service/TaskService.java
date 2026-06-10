package com.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.task.common.BusinessException;
import com.task.entity.Task;
import com.task.entity.User;
import com.task.mapper.TaskMapper;
import com.task.mapper.UserMapper;
import com.task.util.TaskNoGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 任务服务（CRUD 层）
 *
 * 职责：
 *   1) 任务的创建、查询、更新、删除
 *   2) 任务列表的分页、筛选、排序
 *   3) 与状态机（TaskStateMachineService）协作，但本身不处理状态流转
 *
 * 设计原则：
 *   - Controller 不直接调 Mapper
 *   - 业务异常抛 BusinessException，由 GlobalExceptionHandler 统一处理
 *
 * @author Mavis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskMapper taskMapper;
    private final UserMapper userMapper;
    private final TaskNoGenerator taskNoGenerator;

    /**
     * 分页查询任务列表（带数据权限过滤）
     *
     * 数据权限规则：
     *   - ADMIN：全部
     *   - MANAGER：本部门所有成员的任务（creator 或 assignee 是本部门成员）
     *   - EMPLOYEE：仅自己（creator 或 assignee）
     *
     * @param currentUser 当前登录用户（用于权限过滤）
     */
    public Page<Task> listTasks(Integer pageNum, Integer pageSize,
                                 String status, Integer priority,
                                 String creatorId, String assigneeId,
                                 User currentUser) {
        if (currentUser == null) {
            throw BusinessException.unauthorized("未登录");
        }

        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Task::getStatus, status);
        }
        if (priority != null) {
            wrapper.eq(Task::getPriority, priority);
        }
        if (creatorId != null && !creatorId.isEmpty()) {
            wrapper.eq(Task::getCreatorId, creatorId);
        }
        if (assigneeId != null && !assigneeId.isEmpty()) {
            wrapper.eq(Task::getAssigneeId, assigneeId);
        }

        // 关键：数据权限过滤
        applyDataPermissionFilter(wrapper, currentUser);

        // 修复（L1）：默认排序改为 "优先级 > 截止时间 > 创建时间"
        // 原因：任务管理应该让"最紧急的最先看到"
        //   1) 优先级 1（最高）在前，4（最低）在后
        //   2) 同优先级下，deadline 越早越靠前（deadline 为 null 的排最后）
        //   3) 同优先级同 deadline，按创建时间倒序
        wrapper.orderByAsc(Task::getPriority)
                .orderByAsc(Task::getActualDeadline); // null 排最后是 MySQL 默认行为
        // created_at desc 作为兜底排序（防止 priority 和 deadline 都 null）
        wrapper.orderByDesc(Task::getCreatedAt);

        Page<Task> page = new Page<>(
                pageNum != null && pageNum > 0 ? pageNum : 1,
                pageSize != null && pageSize > 0 ? pageSize : 10);
        return taskMapper.selectPage(page, wrapper);
    }

    /**
     * 根据 ID 查询任务详情（带权限校验）
     */
    public Task getTaskById(Long id, User currentUser) {
        if (id == null) {
            throw BusinessException.badRequest("任务 ID 不能为空");
        }
        if (currentUser == null) {
            throw BusinessException.unauthorized("未登录");
        }
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw BusinessException.notFound("任务不存在: " + id);
        }

        // 权限校验：非管理员且非任务相关人，禁止查看
        if (!hasDataPermission(task, currentUser)) {
            log.warn("[任务] 用户 {} 无权查看任务 {}（数据权限拒绝）",
                    currentUser.getUserId(), id);
            throw BusinessException.notFound("任务不存在: " + id);
        }
        return task;
    }

    /**
     * 创建任务
     */
    @Transactional(rollbackFor = Exception.class)
    public Task createTask(Task task) {
        validateTask(task);
        task.setTaskNo(taskNoGenerator.generate());
        task.setStatus("PENDING_ACCEPT");
        task.setVersion(0);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());

        taskMapper.insert(task);
        log.info("[任务] 创建任务: id={}, taskNo={}, title={}",
                task.getId(), task.getTaskNo(), task.getTitle());
        return task;
    }

    /**
     * 更新任务（不允许修改状态）
     */
    @Transactional(rollbackFor = Exception.class)
    public Task updateTask(Long id, Task task, User currentUser) {
        Task existing = getTaskById(id, currentUser);
        if (!Objects.equals(existing.getStatus(), "PENDING_ACCEPT")) {
            throw BusinessException.badRequest("只有待接收状态的任务可以编辑");
        }
        // 只能更新自己创建的任务
        if (!Objects.equals(existing.getCreatorId(), currentUser.getUserId())) {
            throw BusinessException.forbidden("只能修改自己创建的任务");
        }

        task.setId(id);
        task.setStatus(null);
        task.setTaskNo(null);
        task.setUpdatedAt(LocalDateTime.now());

        taskMapper.updateById(task);
        log.info("[任务] 更新任务: id={}, operator={}", id, currentUser.getUserId());
        return getTaskById(id, currentUser);
    }

    /**
     * 删除任务（仅自己发给自己）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteTask(Long id, String currentUserId) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw BusinessException.notFound("任务不存在: " + id);
        }
        if (!Boolean.TRUE.equals(task.getSelfAssigned())
                || !Objects.equals(task.getCreatorId(), currentUserId)) {
            throw BusinessException.forbidden("只能删除自己发给自己的任务");
        }
        taskMapper.deleteById(id);
        log.info("[任务] 删除任务: id={}, operator={}", id, currentUserId);
    }

    /**
     * 查询超时任务列表
     */
    public List<Task> findOverdueTasks() {
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(Task::getActualDeadline, LocalDateTime.now())
                .in(Task::getStatus, List.of("PENDING_ACCEPT", "IN_PROGRESS", "PENDING_VERIFY"));
        return taskMapper.selectList(wrapper);
    }

    private void validateTask(Task task) {
        if (task == null) {
            throw BusinessException.badRequest("任务数据不能为空");
        }
        if (task.getTitle() == null || task.getTitle().isBlank()) {
            throw BusinessException.badRequest("任务标题不能为空");
        }
        if (task.getCreatorId() == null || task.getCreatorId().isBlank()) {
            throw BusinessException.badRequest("创建人不能为空");
        }
        if (task.getAssigneeId() == null || task.getAssigneeId().isBlank()) {
            throw BusinessException.badRequest("执行人不能为空");
        }
        if (task.getPriority() == null || task.getPriority() < 1 || task.getPriority() > 4) {
            throw BusinessException.badRequest("优先级必须在 1-4 之间");
        }
        if (task.getEstimatedDuration() != null && task.getEstimatedDuration() <= 0) {
            throw BusinessException.badRequest("预估时长必须大于 0");
        }
    }

    // ============================================
    // 数据权限过滤（按角色隔离任务可见范围）
    // ============================================

    /**
     * 数据权限过滤（按角色限制可见范围）
     *
     * 规则：
     *   - ADMIN：全部（不添加过滤条件）
     *   - MANAGER：本部门所有成员的任务（creator 或 assignee 属于本部门）
     *   - EMPLOYEE：仅自己（creator 或 assignee 是自己）
     */
    private void applyDataPermissionFilter(LambdaQueryWrapper<Task> wrapper, User currentUser) {
        String role = currentUser.getRole();
        String userId = currentUser.getUserId();

        if ("ADMIN".equals(role)) {
            return;
        }

        if ("MANAGER".equals(role)) {
            if (currentUser.getDepartmentId() == null) {
                // 经理未关联部门：返回空集
                log.warn("[任务] MANAGER {} 未关联部门，返回空集", userId);
                wrapper.eq(Task::getId, -1L);
                return;
            }
            List<String> deptUserIds = userMapper.selectUserIdsByDeptId(
                    currentUser.getDepartmentId());
            if (deptUserIds.isEmpty()) {
                wrapper.eq(Task::getId, -1L);
                return;
            }
            wrapper.and(w -> w.in(Task::getCreatorId, deptUserIds)
                    .or().in(Task::getAssigneeId, deptUserIds));
            return;
        }

        // EMPLOYEE：仅自己
        wrapper.and(w -> w.eq(Task::getCreatorId, userId)
                .or().eq(Task::getAssigneeId, userId));
    }

    /**
     * 单条任务权限校验
     * 返回 true 表示有权查看
     */
    private boolean hasDataPermission(Task task, User currentUser) {
        String role = currentUser.getRole();
        String userId = currentUser.getUserId();

        if ("ADMIN".equals(role)) {
            return true;
        }

        if ("MANAGER".equals(role)) {
            if (currentUser.getDepartmentId() == null) {
                return false;
            }
            // 检查 creator 或 assignee 是否在本部门
            List<String> deptUserIds = userMapper.selectUserIdsByDeptId(
                    currentUser.getDepartmentId());
            if (deptUserIds.isEmpty()) {
                return false;
            }
            return deptUserIds.contains(task.getCreatorId())
                    || deptUserIds.contains(task.getAssigneeId());
        }

        // EMPLOYEE
        return userId.equals(task.getCreatorId())
                || userId.equals(task.getAssigneeId());
    }
}
