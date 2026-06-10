package com.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.task.entity.TaskStatusHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务状态历史 Mapper
 */
@Mapper
public interface TaskStatusHistoryMapper extends BaseMapper<TaskStatusHistory> {
}
