package com.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 历史任务归档实体
 *
 * 对应表：tasks_history_archive
 * 用途：存储已完结超过 archive.interval-months 配置时长的历史任务
 * 注意：本表不设外键约束，独立存储，避免历史数据影响主表性能
 *
 * @author Mavis
 */
@Data
@TableName("tasks_history_archive")
public class TaskHistoryArchive {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务编号（业务唯一） */
    private String taskNo;

    /** 任务标题 */
    private String title;

    /** 任务描述 */
    private String description;

    /** 优先级：1-最高，2-高，3-中，4-低 */
    private Integer priority;

    /** 创建人 UserID（企微） */
    private String creatorId;

    /** 执行人 UserID（企微） */
    private String assigneeId;

    /** 预估时长（分钟） */
    private Integer estimatedDuration;

    /** 实际开始时间 */
    private LocalDateTime actualStartTime;

    /** 实际截止时间 */
    private LocalDateTime actualDeadline;

    /** 最终状态（COMPLETED / WITHDRAWN） */
    private String status;

    /** 来源备注 */
    private String sourceRemark;

    /** 是否自己发给自己 */
    private Boolean selfAssigned;

    /** 完成时间 */
    private LocalDateTime completedAt;

    /** 撤回时间 */
    private LocalDateTime withdrawnAt;

    /** 归档时间 */
    private LocalDateTime archivedAt;

    /** 原始创建时间 */
    private LocalDateTime originalCreatedAt;

    /** 原始更新时间 */
    private LocalDateTime originalUpdatedAt;
}
