package com.task.privacy;

import org.springframework.stereotype.Component;

/**
 * 脱敏工具
 *
 * 实现各类敏感数据的脱敏规则
 *
 * @author Mavis
 */
@Component
public class DesensitizationUtil {

    /**
     * 脱敏入口
     *
     * @param value 原始值
     * @param type  敏感类型
     * @return 脱敏后的值
     */
    public String desensitize(String value, SensitiveType type) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return switch (type) {
            case NAME -> name(value);
            case ID_CARD -> idCard(value);
            case BANK_CARD -> bankCard(value);
            case MOBILE -> mobile(value);
            case EMAIL -> email(value);
            case SALARY -> "****";
            case CUSTOM -> "****";
        };
    }

    /**
     * 姓名脱敏：张三丰 → 张*丰
     * 修复（m9）：单字符也脱敏，避免完全泄露
     */
    private String name(String value) {
        if (value.length() == 1) {
            return "*";
        }
        if (value.length() == 2) {
            return value.charAt(0) + "*";
        }
        return value.charAt(0) + "*".repeat(value.length() - 2) + value.charAt(value.length() - 1);
    }

    /**
     * 身份证脱敏：110123456789012345 → 1101234**********
     * 保留前 7 位 + 后 4 位
     */
    private String idCard(String value) {
        if (value.length() <= 7) {
            return value;
        }
        String prefix = value.substring(0, 7);
        String suffix = value.length() > 11 ? value.substring(value.length() - 4) : "";
        return prefix + "*".repeat(Math.max(0, value.length() - 7 - suffix.length())) + suffix;
    }

    /**
     * 银行卡脱敏：6222021234567890 → ************7890
     */
    private String bankCard(String value) {
        if (value.length() <= 4) {
            return value;
        }
        return "*".repeat(value.length() - 4) + value.substring(value.length() - 4);
    }

    /**
     * 手机号脱敏：13812345678 → 138****5678
     */
    private String mobile(String value) {
        if (value.length() != 11) {
            if (value.length() <= 4) {
                return value;
            }
            return value.substring(0, 2) + "*".repeat(value.length() - 4) + value.substring(value.length() - 2);
        }
        return value.substring(0, 3) + "****" + value.substring(7);
    }

    /**
     * 邮箱脱敏：abc@example.com → a**@example.com
     */
    private String email(String value) {
        int atIdx = value.indexOf('@');
        if (atIdx <= 1) {
            return value;
        }
        if (atIdx == 2) {
            return value.charAt(0) + "*" + value.substring(atIdx);
        }
        return value.charAt(0) + "*".repeat(atIdx - 1) + value.substring(atIdx);
    }
}
