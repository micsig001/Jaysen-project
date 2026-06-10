package com.task.audit;

import lombok.Data;

import java.util.Map;

/**
 * 审计日志上下文
 *
 * 由 AuditLogAspect 构造，包含一条审计记录所需的全部信息
 *
 * @author Mavis
 */
@Data
public class AuditLogContext {

    /** 操作人 UserID */
    private String operatorId;

    /** 操作人姓名 */
    private String operatorName;

    /** 操作类型 */
    private String operationType;

    /** 资源类型 */
    private String resourceType;

    /** 资源 ID */
    private Long resourceId;

    /** 操作前快照 */
    private Object beforeSnapshot;

    /** 操作后快照 */
    private Object afterSnapshot;

    /** IP 地址 */
    private String ipAddress;

    /** 用户代理 */
    private String userAgent;

    /** 扩展字段（业务自定义） */
    private Map<String, Object> extras;
}
