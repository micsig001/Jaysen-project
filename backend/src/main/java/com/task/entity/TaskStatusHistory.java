package com.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务状态历史实体类
 */
@Data
@TableName("task_status_history")
public class TaskStatusHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务 ID
     */
    private Long taskId;

    /**
     * 旧状态（修复 P0 bug：与 V1 SQL 字段 from_status 保持一致）
     */
    private String fromStatus;

    /**
     * 新状态（修复 P0 bug：与 V1 SQL 字段 to_status 保持一致）
     */
    private String toStatus;

    /**
     * 操作人 ID
     */
    private String operatorId;

    /**
     * 操作备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
