package com.task.user;

import com.task.audit.AuditLog;
import com.task.common.BusinessException;
import com.task.common.Result;
import com.task.user.dto.RoleChangeRequest;
import com.task.user.dto.RoleStatsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色管理 Controller
 *
 * <p>接口列表：</p>
 * <ul>
 *   <li>GET  /api/users/{userId}/role  查询用户角色（ADMIN / MANAGER）</li>
 *   <li>PUT  /api/users/{userId}/role  修改用户角色（仅 ADMIN）</li>
 *   <li>GET  /api/users/role-stats     各角色人数统计（ADMIN / MANAGER）</li>
 * </ul>
 *
 * @author Mavis
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "角色管理", description = "查询 / 修改用户角色，统计分布")
public class RoleController {

    private final RoleService roleService;

    /**
     * 查询用户角色
     * 权限：ADMIN / MANAGER
     */
    @GetMapping("/{userId}/role")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "查询用户角色", description = "ADMIN / MANAGER 可用")
    public Result<String> getUserRole(@PathVariable String userId) {
        return Result.success(roleService.getUserRole(userId));
    }

    /**
     * 修改用户角色
     * 权限：仅 ADMIN
     * 自动标记 is_manual_role=true，企微同步不再覆盖
     */
    @PutMapping("/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operationType = "CHANGE_ROLE", resourceType = "USER",
            resourceIdParam = "#userId", description = "修改用户角色")
    @Operation(summary = "修改用户角色", description = "仅 ADMIN 可用；不能改自己；至少保留 1 个 ADMIN")
    public Result<Void> changeUserRole(@PathVariable String userId,
                                       @Valid @RequestBody RoleChangeRequest request) {
        String operatorId = currentUserId();
        roleService.changeUserRole(userId, request.getNewRole(), operatorId);
        return Result.success();
    }

    /**
     * 各角色人数统计
     * 权限：ADMIN / MANAGER（用于管理面板展示）
     */
    @GetMapping("/role-stats")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "各角色人数统计",
            description = "返回 [{role, count}, ...] 数组，包含 EMPLOYEE / MANAGER / ADMIN 三种角色")
    public Result<List<RoleStatsVO>> getRoleStats() {
        return Result.success(roleService.getRoleStats());
    }

    // ============================================
    // 工具方法
    // ============================================

    private String currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null) {
            throw BusinessException.unauthorized("未登录");
        }
        return principal.toString();
    }
}
