package com.task.wework;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WeWork API 客户端 Bean 注册配置
 *
 * <p>根据 Spring profile 选择激活的 {@link WeWorkApiClient} 实现：
 * <ul>
 *   <li>{@code dev} / {@code test} —— 注册 {@link FakeWeWorkApiClient}（{@code @Primary}），
 *       端到端跑通 OAuth 流程无需企微 corp 账号</li>
 *   <li>其它 profile（{@code prod}、未设置等）—— 注册真实 {@link WeWorkApiClient}</li>
 * </ul>
 *
 * <p>真实 / Fake 不能同时存在；{@link ConditionalOnMissingBean} 兜底防止重复注册。
 *
 * @author Mavis
 */
@Slf4j
@Configuration
public class WeWorkApiConfig {

    /**
     * 假客户端（dev / test 环境）—— @Primary
     */
    @Bean
    @Primary
    @Profile({"dev", "test"})
    public WeWorkApiClient fakeWeWorkApiClient() {
        log.info("[WeWork] 注册 FakeWeWorkApiClient（dev/test profile），OAuth 走假数据");
        return new FakeWeWorkApiClient();
    }

    /**
     * 真实客户端（prod / 默认环境）—— 非 Primary
     * <p>需要 WebClient.Builder、StringRedisTemplate、ObjectMapper 等基础设施；
     * 依赖缺一不可，所以没有 @Primary，避免与 Fake 冲突。
     */
    @Bean
    @ConditionalOnMissingBean(WeWorkApiClient.class)
    public WeWorkApiClient realWeWorkApiClient(
            WebClient.Builder webClientBuilder,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        log.info("[WeWork] 注册 真实 WeWorkApiClient（默认/prod profile）");
        return new WeWorkApiClient(webClientBuilder, redisTemplate, objectMapper);
    }
}
