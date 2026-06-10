package com.task.archive.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 历史任务返回 VO
 *
 * 字段命名与前端保持一致（驼峰）
 * 注意：敏感数据（描述、备注）不在此 VO 中返回，必要时单独处理
 *
 * @author Mavis
 */
@Data
public class ArchiveTaskVO {

    /** 归档记录 ID */
    private Long id;

    /** 任务编号（业务唯一） */
    private String taskNo;

    /** 任务标题 */
    private String title;

    /** 任务描述 */
    private String description;

    /** 优先级：1-最高，2-高，3-中，4-低 */
    private Integer priority;

    /** 创建人 UserID */
    private String creatorId;

    /** 创建人姓名（关联 users.name 冗余） */
    private String creatorName;

    /** 执行人 UserID */
    private String assigneeId;

    /** 执行人姓名 */
    private String assigneeName;

    /** 预估时长（分钟） */
    private Integer estimatedDuration;

    /** 实际开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime actualStartTime;

    /** 实际截止时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime actualDeadline;

    /** 最终状态 */
    private String status;

    /** 来源备注 */
    private String sourceRemark;

    /** 是否自己发给自己 */
    private Boolean selfAssigned;

    /** 完成时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;

    /** 撤回时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime withdrawnAt;

    /** 归档时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime archivedAt;

    /** 原始创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime originalCreatedAt;

    /** 原始更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime originalUpdatedAt;
}
