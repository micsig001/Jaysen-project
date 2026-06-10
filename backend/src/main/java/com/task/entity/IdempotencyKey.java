package com.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 幂等性键值实体
 *
 * 对应表：idempotency_keys
 * 用途：记录请求幂等性 Key 的生命周期，配合 Redis 做幂等性保障
 *
 * 三重机制（详见 IdempotencyService）：
 *   1) 客户端 UUID v4 作为 X-Idempotency-Key
 *   2) Redis SETNX 检查
 *   3) 数据库 UNIQUE 约束兜底
 *
 * @author Mavis
 */
@Data
@TableName("idempotency_keys")
public class IdempotencyKey {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 幂等性 Key（UUID v4，全局唯一） */
    private String idempotencyKey;

    /** 操作类型（CREATE_TASK / UPDATE_USER_ROLE 等） */
    private String operationType;

    /** 响应数据（JSON 序列化） */
    private String responseData;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 过期时间（默认 24h） */
    private LocalDateTime expiresAt;
}
