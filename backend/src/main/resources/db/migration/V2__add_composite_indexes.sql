-- V2: 添加复合索引以优化查询性能

-- 任务表复合索引
-- 优化：按执行人+状态查询任务列表
ALTER TABLE tasks ADD INDEX idx_assignee_status (assignee_id, status);

-- 优化：按创建人+状态查询任务列表
ALTER TABLE tasks ADD INDEX idx_creator_status (creator_id, status);

-- 优化：按状态+创建时间倒序查询（常见列表排序）
ALTER TABLE tasks ADD INDEX idx_status_created (status, created_at DESC);

-- 优化：超时检查查询（按截止时间+状态）
ALTER TABLE tasks ADD INDEX idx_deadline_status (actual_deadline, status);

-- 任务状态历史表索引
-- 优化：按任务ID+操作时间查询历史记录
ALTER TABLE task_status_history ADD INDEX idx_task_created (task_id, created_at);

-- 审计日志表索引
-- 优化：按操作人+操作时间查询审计记录
ALTER TABLE audit_log ADD INDEX idx_operator_time (operator_id, operation_time);
