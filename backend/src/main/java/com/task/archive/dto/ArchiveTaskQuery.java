package com.task.archive.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 历史任务查询入参
 *
 * 支持的筛选条件：
 *   - 关键词（标题模糊搜索）
 *   - 状态（精确匹配，多个用逗号分隔）
 *   - 创建人（精确匹配）
 *   - 执行人（精确匹配）
 *   - 归档时间范围
 *   - 原始创建时间范围
 *   - 优先级
 *
 * 权限过滤由 Service 层根据当前用户角色动态添加：
 *   - EMPLOYEE：仅自己的任务（creator_id = currentUser.userId OR assignee_id = currentUser.userId）
 *   - MANAGER：本部门所有成员的任务
 *   - ADMIN：全部
 *
 * @author Mavis
 */
@Data
public class ArchiveTaskQuery {

    /** 关键词：搜索标题 */
    private String keyword;

    /** 状态筛选：COMPLETED / WITHDRAWN，多个用逗号分隔 */
    private String status;

    /** 创建人 UserID（精确匹配） */
    private String creatorId;

    /** 执行人 UserID（精确匹配） */
    private String assigneeId;

    /** 归档开始时间（>=） */
    private LocalDateTime archivedAtStart;

    /** 归档结束时间（<=） */
    private LocalDateTime archivedAtEnd;

    /** 原始创建开始时间（>=） */
    private LocalDateTime originalCreatedAtStart;

    /** 原始创建结束时间（<=） */
    private LocalDateTime originalCreatedAtEnd;

    /** 优先级（1-4） */
    private Integer priority;

    /** 页码（从1开始） */
    private Integer pageNum = 1;

    /** 每页大小 */
    private Integer pageSize = 10;
}
