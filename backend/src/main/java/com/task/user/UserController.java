package com.task.user;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.task.audit.AuditLog;
import com.task.common.BusinessException;
import com.task.common.Result;
import com.task.entity.User;
import com.task.mapper.UserMapper;
import com.task.user.dto.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理 Controller
 *
 * <p>接口列表：</p>
 * <ul>
 *   <li>GET    /api/users                 分页查询用户列表（ADMIN / MANAGER）</li>
 *   <li>GET    /api/users/{userId}        查询用户详情（ADMIN / MANAGER / 本人）</li>
 *   <li>POST   /api/users/{userId}/disable 禁用账号（仅 ADMIN）</li>
 *   <li>POST   /api/users/{userId}/enable  启用账号（仅 ADMIN）</li>
 * </ul>
 *
 * @author Mavis
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户列表 / 详情 / 启停账号")
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    /**
     * 分页查询用户列表
     * 权限：ADMIN 看全部，MANAGER 仅能看自己部门的成员
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "分页查询用户列表",
            description = "ADMIN 可见全部；MANAGER 自动按本部门过滤。参数均可选")
    public Result<Page<UserVO>> listUsers(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @Parameter(description = "关键词（匹配 userId / name / mobile / email）")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "角色筛选（EMPLOYEE / MANAGER / ADMIN）")
            @RequestParam(required = false) String role,
            @Parameter(description = "部门 ID 筛选（ADMIN 可用，MANAGER 自动忽略）")
            @RequestParam(required = false) Long deptId,
            @Parameter(description = "状态筛选（1-启用 / 0-禁用）")
            @RequestParam(required = false) Integer status
    ) {
        User current = currentUser();

        // MANAGER 只能看本部门；ADMIN 可指定 deptId
        if ("MANAGER".equals(current.getRole())) {
            if (current.getDepartmentId() == null) {
                log.warn("[用户列表] MANAGER {} 未关联部门，返回空集", current.getUserId());
                Page<UserVO> empty = new Page<>(pageNum, pageSize, 0);
                return Result.success(empty);
            }
            deptId = current.getDepartmentId(); // 强制覆盖
        }

        Page<UserVO> page = userService.listUsers(pageNum, pageSize, keyword, role, deptId, status);
        return Result.success(page);
    }

    /**
     * 查询用户详情
     * 权限：ADMIN / MANAGER / 本人
     */
    @GetMapping("/{userId}")
    @Operation(summary = "查询用户详情",
            description = "ADMIN / MANAGER 任意可查；EMPLOYEE 仅能查自己")
    public Result<UserVO> getUserDetail(@PathVariable String userId) {
        User current = currentUser();
        // 非管理员只能查自己
        if (!"ADMIN".equals(current.getRole())
                && !"MANAGER".equals(current.getRole())
                && !userId.equals(current.getUserId())) {
            throw BusinessException.forbidden("无权查看其他用户详情");
        }
        return Result.success(userService.getUserById(userId));
    }

    /**
     * 禁用用户账号
     * 权限：仅 ADMIN
     */
    @PostMapping("/{userId}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operationType = "DISABLE", resourceType = "USER",
            resourceIdParam = "#userId", description = "禁用用户账号")
    @Operation(summary = "禁用用户账号", description = "仅 ADMIN 可用；不能禁用自己或管理员")
    public Result<Void> disableUser(@PathVariable String userId) {
        userService.disableUser(userId, currentUserId());
        return Result.success();
    }

    /**
     * 启用用户账号
     * 权限：仅 ADMIN
     */
    @PostMapping("/{userId}/enable")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operationType = "ENABLE", resourceType = "USER",
            resourceIdParam = "#userId", description = "启用用户账号")
    @Operation(summary = "启用用户账号", description = "仅 ADMIN 可用")
    public Result<Void> enableUser(@PathVariable String userId) {
        userService.enableUser(userId, currentUserId());
        return Result.success();
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

    private User currentUser() {
        String userId = currentUserId();
        User user = userMapper.selectByUserId(userId);
        if (user == null) {
            throw BusinessException.unauthorized("用户不存在: " + userId);
        }
        return user;
    }
}
