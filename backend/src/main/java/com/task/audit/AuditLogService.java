package com.task.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.task.entity.AuditLog;
import com.task.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 审计日志服务
 *
 * 负责将审计记录异步写入 audit_log 表
 * 使用 @Async 异步处理，不阻塞主业务流程
 *
 * @author Mavis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    /**
     * 异步写入审计日志
     *
     * @param context 审计上下文（包含操作人、资源、操作类型等）
     */
    @Async
    public void recordAsync(AuditLogContext context) {
        try {
            AuditLog record = new AuditLog();
            record.setOperatorId(context.getOperatorId());
            record.setOperatorName(context.getOperatorName());
            record.setOperationType(context.getOperationType());
            record.setResourceType(context.getResourceType());
            record.setResourceId(context.getResourceId());
            record.setBeforeSnapshot(toJson(context.getBeforeSnapshot()));
            record.setAfterSnapshot(toJson(context.getAfterSnapshot()));
            record.setIpAddress(context.getIpAddress());
            record.setUserAgent(context.getUserAgent());
            record.setCreatedAt(LocalDateTime.now());

            auditLogMapper.insert(record);
            log.debug("[审计] 写入审计日志: operationType={}, resourceType={}, resourceId={}",
                    context.getOperationType(), context.getResourceType(), context.getResourceId());
        } catch (Exception e) {
            // 审计日志写入失败不能影响主业务
            log.error("[审计] 写入审计日志失败: {}", e.getMessage(), e);
        }
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("[审计] 序列化快照失败，使用 toString: {}", e.getMessage());
            return obj.toString();
        }
    }
}
