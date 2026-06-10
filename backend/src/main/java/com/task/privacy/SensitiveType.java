package com.task.privacy;

/**
 * 敏感数据类型枚举
 *
 * <p>与 {@link SensitiveData} 注解配合使用，决定 {@link DesensitizationUtil} 走哪条脱敏规则。
 *
 * <p>脱敏规则：
 * <ul>
 *   <li>{@link #NAME}       姓名 → 保留首字 + 末字，中间 *</li>
 *   <li>{@link #ID_CARD}    身份证 → 保留前 7 + 后 4，中间 *</li>
 *   <li>{@link #BANK_CARD}  银行卡 → 保留后 4，前面全 *</li>
 *   <li>{@link #MOBILE}     手机号 → 前 3 + **** + 后 4</li>
 *   <li>{@link #EMAIL}      邮箱 → @前保留首字，后面 *</li>
 *   <li>{@link #SALARY}     薪资 → 全 *</li>
 *   <li>{@link #CUSTOM}     自定义（保留扩展点）</li>
 * </ul>
 *
 * @author Mavis
 */
public enum SensitiveType {

    /** 姓名 */
    NAME,

    /** 身份证号 */
    ID_CARD,

    /** 银行卡号 */
    BANK_CARD,

    /** 手机号 */
    MOBILE,

    /** 邮箱 */
    EMAIL,

    /** 薪资 */
    SALARY,

    /** 自定义（默认全 *，业务可在 DesensitizationUtil 中扩展） */
    CUSTOM
}
