package com.task.user.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户返回 VO（View Object）
 *
 * <p>用于：</p>
 * <ul>
 *   <li>GET /api/users 分页查询</li>
 *   <li>GET /api/users/{userId} 详情</li>
 * </ul>
 *
 * <p>字段命名与前端保持一致（驼峰）</p>
 * <p>关联字段（部门名）已冗余展示，避免前端二次请求</p>
 *
 * @author Mavis
 */
@Data
public class UserVO {

    /** 系统主键 ID */
    private Long id;

    /** 企业微信 UserID */
    private String userId;

    /** 姓名 */
    private String name;

    /** 头像 URL */
    private String avatarUrl;

    /** 系统角色：EMPLOYEE / MANAGER / ADMIN */
    private String role;

    /** 部门 ID（users.department_id） */
    private Long departmentId;

    /** 部门名称（关联 departments.name 冗余） */
    private String departmentName;

    /** 职位 */
    private String position;

    /** 状态：1-启用，0-禁用 */
    private Integer status;

    /** 是否手动分配角色（true=手动，企微同步不会覆盖） */
    private Boolean manualRole;

    /** 最后同步时间（来自企微） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastSyncTime;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
