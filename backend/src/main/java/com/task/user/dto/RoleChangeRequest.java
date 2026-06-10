package com.task.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 修改用户角色请求 DTO
 *
 * <p>用于：PUT /api/users/{userId}/role</p>
 *
 * <p>业务规则：</p>
 * <ul>
 *   <li>role 必须是 EMPLOYEE / MANAGER / ADMIN 之一</li>
 *   <li>不能修改自己的角色（业务校验在 Service 层）</li>
 *   <li>只有 ADMIN 可以调用（鉴权在 Controller 层）</li>
 * </ul>
 *
 * @author Mavis
 */
@Data
public class RoleChangeRequest {

    /**
     * 新角色
     * 合法值：EMPLOYEE / MANAGER / ADMIN
     */
    @NotBlank(message = "角色不能为空")
    @Pattern(
            regexp = "^(EMPLOYEE|MANAGER|ADMIN)$",
            message = "角色必须是 EMPLOYEE / MANAGER / ADMIN 之一"
    )
    private String newRole;
}
