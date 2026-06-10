package com.task.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色统计 VO
 *
 * <p>用于：GET /api/users/role-stats（实际上由 RoleController 提供）</p>
 *
 * <p>返回结构：[{role: 'EMPLOYEE', count: 50}, {role: 'MANAGER', count: 8}, ...]</p>
 *
 * @author Mavis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleStatsVO {

    /** 角色名：EMPLOYEE / MANAGER / ADMIN */
    private String role;

    /** 该角色下的用户数 */
    private Long count;
}
