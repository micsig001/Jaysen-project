package com.task.permission;

import com.task.common.BusinessException;
import com.task.entity.Task;
import com.task.entity.User;
import com.task.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 权限服务（业务级权限校验）
 *
 * <p>区别于 {@code @PreAuthorize} 的"角色级"控制，
 * 本服务做"资源级"控制：判断当前用户对特定资源（如某个任务）有无操作权限。
 *
 * <p>典型场景：
 * <pre>
 *   - 任务编辑：仅创建者 + PENDING_ACCEPT 状态
 *   - 任务删除：仅创建者（且 self_assigned=true）
 *   - 任务状态流转：见 TaskStateMachineService
 *   - 用户角色变更：仅 ADMIN
 *   - 部门管理：仅 ADMIN（部门树变更）
 * </pre>
 *
 * <p>用法：
 * <pre>
 *   if (!permissionService.canEditTask(task, currentUser)) {
 *       throw BusinessException.forbidden("无权编辑此任务");
 *   }
 * </pre>
 *
 * @author Mavis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    /** 角色权限集合：角色 -> 可执行操作 */
    private static final Map<String, Set<String>> ROLE_PRIVILEGES = Map.of(
            "ADMIN", Set.of("TASK_CREATE", "TASK_EDIT_ALL", "TASK_DELETE_ALL",
                    "USER_MANAGE", "ROLE_MANAGE", "DEPT_MANAGE", "AUDIT_VIEW",
                    "ARCHIVE_TRIGGER", "SYNC_TRIGGER"),
            "MANAGER", Set.of("TASK_CREATE", "TASK_EDIT_DEPT", "TASK_VIEW_DEPT",
                    "ARCHIVE_VIEW"),
            "EMPLOYEE", Set.of("TASK_CREATE", "TASK_EDIT_SELF", "TASK_VIEW_SELF")
    );

    private final UserMapper userMapper;

    /**
     * 部门用户 ID 缓存（同 TaskService 的缓存）
     * 这里复制一份是为了不破坏 TaskService 的封装；可以后续提到 BaseService
     */
    private static final long CACHE_TTL_MS = 60_000L;
    private final Map<Long, DeptUserCache> deptUserCache = new ConcurrentHashMap<>();

    private record DeptUserCache(List<String> userIds, long expireAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }

    // ============================================
    // 1. 角色级权限（粗粒度）
    // ============================================

    /**
     * 当前用户是否有指定权限（角色级）
     */
    public boolean hasPrivilege(User user, String privilege) {
        if (user == null || user.getRole() == null) {
            return false;
        }
        Set<String> privileges = ROLE_PRIVILEGES.get(user.getRole());
        return privileges != null && privileges.contains(privilege);
    }

    /**
     * 断言当前用户有指定权限，无权则抛 403
     */
    public void requirePrivilege(User user, String privilege) {
        if (!hasPrivilege(user, privilege)) {
            throw BusinessException.forbidden("无权限执行此操作: " + privilege);
        }
    }

    /**
     * 是否 ADMIN
     */
    public boolean isAdmin(User user) {
        return user != null && "ADMIN".equals(user.getRole());
    }

    /**
     * 是否 MANAGER
     */
    public boolean isManager(User user) {
        return user != null && "MANAGER".equals(user.getRole());
    }

    // ============================================
    // 2. 资源级权限（细粒度）
    // ============================================

    /**
     * 能否查看任务
     * 规则：ADMIN 全部 / MANAGER 本部门 / EMPLOYEE 仅自己相关
     */
    public boolean canViewTask(Task task, User user) {
        if (task == null || user == null) return false;
        if (isAdmin(user)) return true;
        if (isManager(user)) {
            return isInUserDept(task.getCreatorId(), user)
                    || isInUserDept(task.getAssigneeId(), user);
        }
        // EMPLOYEE
        return Objects.equals(task.getCreatorId(), user.getUserId())
                || Objects.equals(task.getAssigneeId(), user.getUserId());
    }

    /**
     * 能否编辑任务（仅创建者 + PENDING_ACCEPT 状态）
     */
    public boolean canEditTask(Task task, User user) {
        if (task == null || user == null) return false;
        // 状态限制
        if (!"PENDING_ACCEPT".equals(task.getStatus())) {
            return false;
        }
        // ADMIN 可编辑全部
        if (isAdmin(user)) return true;
        // 其他角色：仅创建者
        return Objects.equals(task.getCreatorId(), user.getUserId());
    }

    /**
     * 能否删除任务
     * 规则：仅自己发给自己（self_assigned=true） + ADMIN 全部
     */
    public boolean canDeleteTask(Task task, User user) {
        if (task == null || user == null) return false;
        if (isAdmin(user)) return true;
        if (Boolean.TRUE.equals(task.getSelfAssigned())
                && Objects.equals(task.getCreatorId(), user.getUserId())) {
            return true;
        }
        return false;
    }

    /**
     * 断言可编辑，否则抛 403
     */
    public void requireEditTask(Task task, User user) {
        if (!canEditTask(task, user)) {
            throw BusinessException.forbidden(
                    "无权编辑此任务（可能不是创建者或状态不允许）");
        }
    }

    /**
     * 断言可查看，否则抛 404（不暴露资源是否存在）
     */
    public void requireViewTask(Task task, User user) {
        if (!canViewTask(task, user)) {
            // 用 404 而非 403，防止泄露任务存在性
            throw BusinessException.notFound("任务不存在");
        }
    }

    // ============================================
    // 3. 部门级辅助
    // ============================================

    /**
     * 判断某 userId 是否在当前用户的部门
     */
    public boolean isInUserDept(String targetUserId, User currentUser) {
        if (currentUser == null || currentUser.getDepartmentId() == null) {
            return false;
        }
        List<String> deptUserIds = getCachedDeptUserIds(currentUser.getDepartmentId());
        return deptUserIds.contains(targetUserId);
    }

    private List<String> getCachedDeptUserIds(Long deptId) {
        DeptUserCache cached = deptUserCache.get(deptId);
        if (cached != null && !cached.isExpired()) {
            return cached.userIds();
        }
        List<String> ids = userMapper.selectUserIdsByDeptId(deptId);
        List<String> safeIds = ids != null ? ids : Collections.emptyList();
        deptUserCache.put(deptId, new DeptUserCache(safeIds,
                System.currentTimeMillis() + CACHE_TTL_MS));
        return safeIds;
    }

    public void invalidateDeptCache(Long deptId) {
        if (deptId == null) {
            deptUserCache.clear();
        } else {
            deptUserCache.remove(deptId);
        }
    }
}
