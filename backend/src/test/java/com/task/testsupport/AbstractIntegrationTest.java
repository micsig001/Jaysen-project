package com.task.testsupport;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * B1：@SpringBootTest 集成测试基类
 *
 * <p>目标：覆盖 Controller 端到端主流程，真实 Spring Context +
 * H2 in-memory DB（MySQL 兼容模式）+ 真实 MyBatis-Plus +
 * Mocked Redis（避免依赖真实 Redis 服务）。
 *
 * <p>配置：
 * <ul>
 *   <li>{@code @ActiveProfiles("test")} → 加载
 *       {@code src/test/resources/application-test.yml}</li>
 *   <li>Redis 自动配置在 application-test.yml 中被排除，
 *       这里 @MockBean 注入 StringRedisTemplate 占位，调用链不会触发真实连接</li>
 *   <li>H2 schema 由 spring.sql.init 在启动时执行
 *       {@code classpath:db/test/schema-h2.sql}</li>
 * </ul>
 *
 * <p>子类需要自行添加 {@code @Import(TestBeansConfig.class)}，
 * 因为 Spring Boot 不会自动发现父类的 @TestConfiguration 内部类。
 *
 * @author Mavis
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    /**
     * Mock Redis：避免测试启动时连接真实 Redis。
     * <p>注：RedisAutoConfiguration 已在 application-test.yml 排除，
     * 此处 @MockBean 仅用于让 StringRedisTemplate 类型可注入（代码中用到的 bean）。
     */
    @MockBean
    protected StringRedisTemplate stringRedisTemplate;
}


