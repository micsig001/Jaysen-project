package com.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务实体类
 */
@Data
@TableName("tasks")
public class Task {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务编号
     */
    @TableField("task_no")
    private String taskNo;

    /**
     * 任务标题
     */
    @NotBlank(message = "任务标题不能为空")
    @Size(max = 200, message = "任务标题不能超过 200 字符")
    private String title;

    /**
     * 任务描述
     */
    @Size(max = 5000, message = "任务描述不能超过 5000 字符")
    private String description;

    /**
     * 优先级：1-最高，2-高，3-中，4-低
     */
    @Min(value = 1, message = "优先级最小为 1")
    @Max(value = 4, message = "优先级最大为 4")
    private Integer priority;

    /**
     * 创建人 UserID（发起方）
     */
    @NotBlank(message = "创建人不能为空")
    @TableField("creator_id")
    private String creatorId;

    /**
     * 执行人 UserID（接收方）
     */
    @NotBlank(message = "执行人不能为空")
    @TableField("assignee_id")
    private String assigneeId;

    /**
     * 预估时长（分钟）
     */
    @Min(value = 1, message = "预估时长必须大于 0")
    @TableField("estimated_duration")
    private Integer estimatedDuration;

    /**
     * 实际开始时间（接收方确认后记录）
     */
    @TableField("actual_start_time")
    private LocalDateTime actualStartTime;

    /**
     * 实际截止时间（根据实际开始时间推算）
     */
    @TableField("actual_deadline")
    private LocalDateTime actualDeadline;

    /**
     * 状态：PENDING_ACCEPT/IN_PROGRESS/PENDING_VERIFY/COMPLETED/WITHDRAWN/REJECTED
     */
    private String status;

    /**
     * 来源备注
     */
    @TableField("source_remark")
    private String sourceRemark;

    /**
     * 是否自己发给自己
     */
    @TableField("is_self_assigned")
    private Boolean selfAssigned;

    /**
     * 完成时间
     */
    @TableField("completed_at")
    private LocalDateTime completedAt;

    /**
     * 撤回时间
     */
    @TableField("withdrawn_at")
    private LocalDateTime withdrawnAt;

    /**
     * 版本号（乐观锁）
     */
    @Version
    private Integer version;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
