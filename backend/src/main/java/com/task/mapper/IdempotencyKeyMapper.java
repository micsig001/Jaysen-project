package com.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.task.entity.IdempotencyKey;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 幂等性键值 Mapper
 *
 * 配合 Redis 做幂等性检查的数据库兜底
 * UNIQUE 约束：idempotency_key 字段
 *
 * @author Mavis
 */
@Mapper
public interface IdempotencyKeyMapper extends BaseMapper<IdempotencyKey> {

    /**
     * 根据 Key 查询记录
     */
    @Select("SELECT * FROM idempotency_keys WHERE idempotency_key = #{key} LIMIT 1")
    IdempotencyKey selectByKey(@Param("key") String key);

    /**
     * 更新响应数据和过期时间
     */
    @Update("UPDATE idempotency_keys SET response_data = #{responseData}, " +
            "expires_at = #{expiresAt} WHERE idempotency_key = #{key}")
    int updateResponseData(@Param("key") String key,
                           @Param("responseData") String responseData,
                           @Param("expiresAt") LocalDateTime expiresAt);

    /**
     * 删除过期记录（由 {@code IdempotencyCleanupScheduler} 每天凌晨调用）
     */
    @Delete("DELETE FROM idempotency_keys WHERE expires_at < #{now}")
    int deleteExpired(@Param("now") LocalDateTime now);

    /**
     * 修复（P1-13）：按 Key 删除单条记录（释放 Key 时调用）
     */
    @Delete("DELETE FROM idempotency_keys WHERE idempotency_key = #{key}")
    int deleteByKey(@Param("key") String key);
}
