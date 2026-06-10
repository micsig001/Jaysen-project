package com.task.archive.dto;

import lombok.Data;

/**
 * 归档执行结果 VO
 *
 * 用于：
 *   - 手动触发归档接口的响应
 *   - 定时任务执行日志（DEBUG 级别打印）
 *
 * @author Mavis
 */
@Data
public class ArchiveResultVO {

    /** 本次执行状态：SUCCESS / FAILED / SKIPPED */
    private String status;

    /** 本次迁移的任务总数 */
    private Integer migratedCount;

    /** 执行的批次数 */
    private Integer batchCount;

    /** 本次执行耗时（毫秒） */
    private Long costMillis;

    /** 触发类型：MANUAL / SCHEDULED */
    private String triggerType;

    /** 操作人 UserID（手动触发时记录） */
    private String operatorId;

    /** 备注信息（如异常原因、跳过原因） */
    private String message;
}
