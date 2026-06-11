package com.task.testsupport;

import com.task.util.TaskNoGenerator;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * B1 测试用 bean 注册：
 * 修复 {@code com.task.util.TaskNoGenerator} 缺少 {@code @Component} 注解导致的
 * @SpringBootTest 启动失败（生产代码用 {@code TaskService(@Autowired TaskNoGenerator)}）。
 *
 * <p>本测试用 bean 只是占位（不依赖其行为），但要让 Spring 上下文能完成装配。
 *
 * @author Mavis
 */
@TestConfiguration
public class TestBeansConfig {

    @Bean
    public TaskNoGenerator taskNoGenerator() {
        return new TaskNoGenerator();
    }
}
