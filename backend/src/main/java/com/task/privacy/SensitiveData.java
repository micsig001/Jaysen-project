package com.task.privacy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 敏感数据脱敏注解
 *
 * 标注在实体字段上，由 SensitiveDataAspect 自动脱敏
 * 配合 application.yml 中的 sensitive-data.enabled 开关使用
 *
 * 脱敏规则（详见 DesensitizationUtil）：
 *   - NAME       姓名 → "张*"（保留首字）
 *   - ID_CARD    身份证 → "1101234**********"（保留前 7 位）
 *   - BANK_CARD  银行卡 → "****5678"（保留后 4 位）
 *   - MOBILE     手机号 → "138****8000"（中间 4 位 *）
 *   - EMAIL      邮箱 → "a***@example.com"（@前保留首字）
 *   - SALARY     薪资 → "****"（完全脱敏）
 *
 * 使用示例：
 *   public class User {
 *       @SensitiveData(type = SensitiveType.ID_CARD)
 *       private String idCard;
 *   }
 *
 * @author Mavis
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SensitiveData {

    /**
     * 敏感数据类型
     */
    SensitiveType type();

    /**
     * 是否对超级管理员（ADMIN）也脱敏
     * 默认 false：ADMIN 可见明文（便于运维）
     */
    boolean maskForAdmin() default false;
}
