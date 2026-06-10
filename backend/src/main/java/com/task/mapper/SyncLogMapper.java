package com.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.task.entity.SyncLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 同步日志 Mapper
 */
@Mapper
public interface SyncLogMapper extends BaseMapper<SyncLog> {
}
