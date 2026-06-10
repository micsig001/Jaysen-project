package com.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.task.entity.TaskHistoryArchive;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 历史任务归档 Mapper
 *
 * 继承 BaseMapper 获得常规 CRUD 能力
 * 归档核心操作通过 XML 自定义 SQL 实现（涉及跨表 INSERT...SELECT）
 *
 * @author Mavis
 */
@Mapper
public interface TaskHistoryArchiveMapper extends BaseMapper<TaskHistoryArchive> {

    /**
     * 统计待归档任务数量
     * SQL：WHERE created_at < 阈值 AND status IN ('COMPLETED','WITHDRAWN')
     *
     * @param thresholdCreatedAt 创建时间阈值（早于该时间的任务才会被统计）
     * @return 待归档任务数
     */
    long countPendingArchive(@Param("thresholdCreatedAt") java.time.LocalDateTime thresholdCreatedAt);

    /**
     * 单批插入归档数据（从主表 tasks 选择性插入）
     * 防雷关键：
     *   1) created_at < 阈值（防新任务被归档）
     *   2) status IN ('COMPLETED','WITHDRAWN')（防进行中任务被归档）
     *   3) LIMIT 限制单批数量（防长跑锁表）
     *
     * @param thresholdCreatedAt 创建时间阈值
     * @param batchSize          单批数量
     * @return 本次插入的归档记录数
     */
    int insertBatchFromTasks(@Param("thresholdCreatedAt") java.time.LocalDateTime thresholdCreatedAt,
                             @Param("batchSize") int batchSize);

    /**
     * 按 taskNo 列表物理删除主表任务
     * 注意：
     *   1) 是物理删除（不是逻辑删除），归档完成后从主表清理
     *   2) 使用 taskNo 关联（不是 id）—— 防止 InnoDB 独立表空间下自增 ID 不共享导致误删
     *
     * @param taskNos 待删除的任务编号列表
     * @return 删除的记录数
     */
    int deleteTasksByTaskNos(@Param("taskNos") List<String> taskNos);

    /**
     * 列出本批刚插入的归档 taskNo
     * 用途：删除主表时按 taskNo（业务唯一编号）精确删
     * 配合 archived_at 区间查询使用
     *
     * @param batchStartTime 本批插入的开始时间
     * @return 本批插入的归档记录 taskNo 列表
     */
    List<String> selectTaskNosByArchivedAt(@Param("batchStartTime") java.time.LocalDateTime batchStartTime);
}
