package com.task.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计日志注解
 *
 * 标注在 Controller / Service 方法上，自动记录操作日志到 audit_log 表
 * 通过 AuditLogAspect 环绕通知实现：
 *   1) 方法执行前：捕获入参快照
 *   2) 方法执行后：捕获返回快照
 *   3) 异步写入数据库（不阻塞主流程）
 *
 * 使用示例：
 *   @AuditLog(operationType = "CREATE_TASK", resourceType = "TASK", resourceIdParam = "task.id")
 *   public Result<Task> createTask(@RequestBody Task task) { ... }
 *
 * @author Mavis
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /**
     * 操作类型：CREATE / UPDATE / DELETE / ACCEPT / REJECT / SUBMIT 等
     */
    String operationType();

    /**
     * 资源类型：TASK / USER / DEPARTMENT / ROLE
     */
    String resourceType();

    /**
     * 资源 ID 表达式（SpEL）
     * 示例："task.id" / "userId" / "#id"
     * 为空时取入参第一个参数的 toString()
     */
    String resourceIdParam() default "";

    /**
     * 操作描述（业务语义）
     * 示例："创建任务"、"删除用户"
     */
    String description() default "";
}
