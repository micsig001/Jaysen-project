-- V3: 归档模块索引优化（兼容 MySQL 5.7 / 8.0+）
--
-- 修改记录：
--   2026-06-10 兼容改造：
--     - 移除 idx_archive_archived_at_desc (archived_at DESC) 降序索引（MySQL 5.7 不支持）
--     - 移除 idx_tasks_status_created_at 冗余索引（V2 已有 idx_status_created）
--     - 移除 idx_archive_status 冗余索引（基数小、收益低）
--     - 降序排序需求依赖 V1 的 idx_archived_at + MySQL 反向扫描即可
--
-- 适用：Milestone 6 - 归档策略优化（防雷设计）

-- ============================================
-- 归档表 - 业务查询索引（仅保留 MySQL 5.7 兼容的索引）
-- ============================================

-- 索引1：创建人+归档时间（员工/经理按权限过滤时使用）
-- 解释：EMPLOYEE/MANAGER 查询时 WHERE creator_id = ? ORDER BY archived_at DESC
ALTER TABLE tasks_history_archive ADD INDEX idx_archive_creator_archived (creator_id, archived_at);

-- 索引2：任务标题模糊搜索（关键词查询）
-- 解释：MySQL InnoDB 的 LIKE 'xxx%' 走索引，'%xxx' 不走
--       这里用前缀索引缓解，复杂搜索建议后期接入 Elasticsearch
ALTER TABLE tasks_history_archive ADD INDEX idx_archive_title (title(64));

-- 索引3：原始创建时间（按时间范围筛选）
-- 解释：WHERE original_created_at BETWEEN ? AND ? ORDER BY original_created_at DESC
ALTER TABLE tasks_history_archive ADD INDEX idx_archive_original_created (original_created_at);

-- ============================================
-- 不再创建以下索引（避免问题）
-- ============================================
--
-- [移除] idx_archive_archived_at_desc (archived_at DESC)
--        原因：降序索引仅 MySQL 8.0+ 支持，5.7 上 DDL 失败导致整个迁移炸
--        替代：V1 的 idx_archived_at 升序索引 + MySQL 反向扫描（BEFORE 8.0 也支持）
--
-- [移除] idx_tasks_status_created_at (status, created_at)
--        原因：V2 已有 idx_status_created (status, created_at DESC)，冗余
--        替代：复用 V2 索引
--
-- [移除] idx_archive_status (status)
--        原因：status 字段基数仅 2（COMPLETED/WITHDRAWN），加单列索引收益低
--        替代：应用层 + idx_archived_at 组合即可
