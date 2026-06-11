package com.task.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0.12：MyBatis-Plus SQL 日志按 profile 拆分的配置加载校验。
 *
 * <p>不启动 Spring Context（避免 mapper/datasource 依赖），直接用
 * {@link YamlPropertySourceLoader} 加载 {@code application.yml} 和
 * {@code application-dev.yml}，断言两个配置文件的 SQL log-impl 值。
 *
 * <p>profile 覆盖语义由 Spring Boot 提供保证（{@code application-{profile}.yml}
 * 在激活 profile 时覆盖基础配置），本测试只锁定两个文件的内容。
 *
 * @author Mavis
 */
@DisplayName("MyBatis 日志 profile 拆分 (P0.12)")
class MybatisLogProfileConfigTest {

    private static final String NO_LOGGING =
            "org.apache.ibatis.logging.nologging.NoLoggingImpl";
    private static final String STDOUT =
            "org.apache.ibatis.logging.stdout.StdOutImpl";

    @Test
    @DisplayName("application.yml 默认 NoLoggingImpl（生产安全）")
    void applicationYml_defaultsToNoLogging() throws Exception {
        PropertySource<?> source = loadYaml("application.yml");
        assertThat(source.getProperty("mybatis-plus.configuration.log-impl"))
                .as("application.yml must default to NoLoggingImpl")
                .isEqualTo(NO_LOGGING);
    }

    @Test
    @DisplayName("application-dev.yml 覆盖为 StdOutImpl（开发可见）")
    void applicationDevYml_overridesToStdOut() throws Exception {
        PropertySource<?> source = loadYaml("application-dev.yml");
        assertThat(source.getProperty("mybatis-plus.configuration.log-impl"))
                .as("application-dev.yml must override to StdOutImpl")
                .isEqualTo(STDOUT);
    }

    @Test
    @DisplayName("dev profile 激活时：基础配置被 dev 覆盖（NoLogging → StdOut）")
    void devProfile_overridesBaseConfig() throws Exception {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");

        // 1) 先加载 application.yml
        env.getPropertySources().addFirst(loadYaml("application.yml"));
        assertThat(env.getProperty("mybatis-plus.configuration.log-impl"))
                .as("before override")
                .isEqualTo(NO_LOGGING);

        // 2) 再加载 application-dev.yml，模拟 Spring profile 覆盖语义
        env.getPropertySources().addFirst(loadYaml("application-dev.yml"));
        assertThat(env.getProperty("mybatis-plus.configuration.log-impl"))
                .as("after dev override")
                .isEqualTo(STDOUT);
    }

    @Test
    @DisplayName("非 dev profile（如 test）：保持 NoLoggingImpl 不被覆盖")
    void nonDevProfile_keepsNoLogging() throws Exception {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");

        env.getPropertySources().addFirst(loadYaml("application.yml"));
        // 注意：dev yml 即使存在也不会自动加载（Spring 只加载激活的 profile 配置）
        // 这里只加载 base，验证非 dev 时不会被覆盖
        assertThat(env.getProperty("mybatis-plus.configuration.log-impl"))
                .isEqualTo(NO_LOGGING);
    }

    private PropertySource<?> loadYaml(String filename) throws Exception {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        Resource resource = new ClassPathResource(filename);
        List<PropertySource<?>> sources = loader.load(filename, resource);
        assertThat(sources).as("yaml " + filename + " should parse").isNotEmpty();
        return sources.get(0);
    }
}
