package com.task.config;

import com.task.testsupport.AbstractIntegrationTest;
import com.task.testsupport.TestBeansConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0.2：Spring Boot Actuator 健康检查端点集成测试。
 *
 * <p>验证 K8s 探针 {@code /actuator/health} 与 {@code /actuator/info}
 * 都已暴露，并且走 SecurityConfig 的白名单（无需鉴权）。
 *
 * @author Mavis
 */
@AutoConfigureMockMvc
@Import(TestBeansConfig.class)
@DisplayName("Actuator 健康检查端点 (P0.2)")
class ActuatorEndpointsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("/actuator/health 返回 status=UP 且无需鉴权")
    void healthEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("/actuator/info 返回 200 且无需鉴权")
    void infoEndpointIsReachable() throws Exception {
        // info 端点（show-details: when-authorized）匿名访问仅返回空 body，
        // 关键在于不放行 401/403。
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }
}
