package com.task.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户实体
 */
@Data
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 企业微信UserID
     */
    private String userId;

    /**
     * 姓名
     */
    private String name;

    /**
     * 头像URL
     */
    private String avatarUrl;

    /**
     * 手机号
     */
    private String mobile;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 所属部门ID
     */
    private Long departmentId;

    /**
     * 职位
     */
    private String position;

    /**
     * 系统角色
     */
    private String role;

    /**
     * 状态：1-启用，0-禁用
     */
    private Integer status;

    /**
     * 是否手动分配角色
     */
    @TableField("is_manual_role")
    private Boolean manualRole;

    /**
     * 最后同步时间
     */
    private LocalDateTime lastSyncTime;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
