package com.task.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 幂等性注解
 *
 * 标注在 Controller 方法上，要求请求必须携带 X-Idempotency-Key 请求头
 * 防止重复提交（网络重试、用户误操作）
 *
 * 工作机制（详见 IdempotencyAspect）：
 *   1) 拦截方法执行
 *   2) 从请求头获取 X-Idempotency-Key
 *   3) Redis SETNX 检查 Key 是否已存在
 *      - 存在：返回缓存结果（重复请求）
 *      - 不存在：执行方法，结果写入 Redis（TTL 24h）
 *   4) 业务异常时删除 Key，允许客户端重试
 *
 * 配合 X-Idempotency-Key 数据库表做持久化兜底（idempotency_keys 表，V1 已建）
 *
 * @author Mavis
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /**
     * 操作类型（用于日志和审计）
     * 示例：CREATE_TASK、UPDATE_USER_ROLE
     */
    String operationType() default "";

    /**
     * Key 过期时间（秒）
     * 默认 24 小时，与 idempotency_keys 表 expires_at 对齐
     */
    long ttlSeconds() default 86400L;
}
