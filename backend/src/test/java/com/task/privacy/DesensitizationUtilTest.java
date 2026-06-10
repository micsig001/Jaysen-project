package com.task.privacy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DesensitizationUtil 单元测试
 *
 * 覆盖所有脱敏规则
 *
 * @author Mavis
 */
class DesensitizationUtilTest {

    private DesensitizationUtil util;

    @BeforeEach
    void setUp() {
        util = new DesensitizationUtil();
    }

    @Test
    @DisplayName("姓名脱敏")
    void nameDesensitization() {
        assertThat(util.desensitize("张三", SensitiveType.NAME)).isEqualTo("张*");
        assertThat(util.desensitize("张三丰", SensitiveType.NAME)).isEqualTo("张*丰");
        // 修复（L2）：单字符也脱敏（之前是 "张"，自相矛盾；现在是 "*"）
        assertThat(util.desensitize("张", SensitiveType.NAME)).isEqualTo("*");
        assertThat(util.desensitize("", SensitiveType.NAME)).isEqualTo("");
        assertThat(util.desensitize(null, SensitiveType.NAME)).isNull();
    }

    @Test
    @DisplayName("身份证脱敏：保留前 7 位 + 后 4 位")
    void idCardDesensitization() {
        assertThat(util.desensitize("110123456789012345", SensitiveType.ID_CARD))
                .isEqualTo("1101234*******2345");
        // 边界：刚好 7 位（不脱敏）
        assertThat(util.desensitize("1234567", SensitiveType.ID_CARD)).isEqualTo("1234567");
    }

    @Test
    @DisplayName("银行卡脱敏：保留后 4 位")
    void bankCardDesensitization() {
        assertThat(util.desensitize("6222021234567890", SensitiveType.BANK_CARD))
                .isEqualTo("************7890");
        // 边界：刚好 4 位
        assertThat(util.desensitize("1234", SensitiveType.BANK_CARD)).isEqualTo("1234");
    }

    @Test
    @DisplayName("手机号脱敏：保留前 3 后 4")
    void mobileDesensitization() {
        assertThat(util.desensitize("13812345678", SensitiveType.MOBILE))
                .isEqualTo("138****5678");
        // 非 11 位
        assertThat(util.desensitize("138123", SensitiveType.MOBILE))
                .isEqualTo("13*123");
    }

    @Test
    @DisplayName("邮箱脱敏：保留 @ 前首字")
    void emailDesensitization() {
        assertThat(util.desensitize("abc@example.com", SensitiveType.EMAIL))
                .isEqualTo("a**@example.com");
        // 单字符
        assertThat(util.desensitize("a@b.com", SensitiveType.EMAIL))
                .isEqualTo("a@b.com");
    }

    @Test
    @DisplayName("薪资脱敏：完全脱敏")
    void salaryDesensitization() {
        assertThat(util.desensitize("20000", SensitiveType.SALARY)).isEqualTo("****");
        assertThat(util.desensitize("abc", SensitiveType.SALARY)).isEqualTo("****");
    }
}
