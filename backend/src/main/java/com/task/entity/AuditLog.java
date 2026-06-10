package com.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志实体类
 */
@Data
@TableName("audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 操作人 ID
     */
    private String operatorId;

    /**
     * 操作人姓名
     */
    private String operatorName;

    /**
     * 操作类型：CREATE/UPDATE/DELETE/ACCEPT/REJECT/SUBMIT等
     */
    private String operationType;

    /**
     * 资源类型：TASK/USER/DEPARTMENT
     */
    private String resourceType;

    /**
     * 资源 ID
     */
    private Long resourceId;

    /**
     * 操作前快照（JSON）
     */
    private String beforeSnapshot;

    /**
     * 操作后快照（JSON）
     */
    private String afterSnapshot;

    /**
     * IP 地址
     */
    private String ipAddress;

    /**
     * 用户代理
     */
    private String userAgent;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
