package com.task.user;

import com.task.testsupport.AbstractIntegrationTest;
import com.task.testsupport.TestBeansConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserController @SpringBootTest 集成测试（B1）
 *
 * <p>使用 H2 in-memory DB + 真实 MyBatis-Plus + 真实 Spring Security + Mocked Redis。
 * 覆盖 UserController 主流程：列表分页 / 详情 / 启停账号。
 *
 * <p>鉴权：使用 {@link #asUser(String, String)} 自定义 RequestPostProcessor，
 * 注入 {@code principal=userId(String)} 的 Authentication —— 这与生产环境
 * （{@code JwtAuthenticationFilter} 写入 SecurityContext 的形态）一致，
 * 避免 {@code @WithMockUser} 那种 principal 是 User 对象导致 toString() 太长的问题。
 *
 * <p>P1.7：所有请求统一加 {@code X-Requested-With: XMLHttpRequest} 头，
 * 通过 {@code CsrfHeaderFilter} 的校验（生产前端 axios 默认带此头）。
 *
 * @author Mavis
 */
@AutoConfigureMockMvc
@Import(TestBeansConfig.class)
@DisplayName("UserController 集成测试（H2 + 真实 Spring Context）")
class UserControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * P1.7：CSRF 防护头。生产前端 axios 实例默认注入；
     * 测试侧通过 helper 统一附加，确保所有 MockMvc 请求通过 CsrfHeaderFilter。
     */
    private static final String CSRF_HEADER = "X-Requested-With";
    private static final String CSRF_VALUE = "XMLHttpRequest";

    @BeforeEach
    void seedData() {
        // 清表（测试隔离：H2 in-memory，schema 已存在；用 DELETE 让自增 ID 累积即可）
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM departments");

        // 部门
        jdbc.update(
                "INSERT INTO departments (dept_id, name, parent_id, order_num, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
                "D001", "Engineering", 0L, 1, LocalDateTime.now(), LocalDateTime.now());

        Long deptId = jdbc.queryForObject(
                "SELECT id FROM departments WHERE dept_id = 'D001'", Long.class);

        // 3 个用户：1 个 ADMIN（自己）+ 1 个 EMPLOYEE（同部门）+ 1 个 MANAGER
        insertUser("admin-001", "Admin User", "ADMIN", deptId, 1);
        insertUser("emp-001", "Emp One", "EMPLOYEE", deptId, 1);
        insertUser("mgr-001", "Manager One", "MANAGER", deptId, 1);
    }

    private void insertUser(String userId, String name, String role, Long deptId, int status) {
        jdbc.update(
                "INSERT INTO users (user_id, name, department_id, role, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                userId, name, deptId, role, status, LocalDateTime.now(), LocalDateTime.now());
    }

    /**
     * 构造一个与 JwtAuthenticationFilter 写出的 Authentication 形态一致的 RequestPostProcessor：
     * principal = userId(String)，authorities = ROLE_xxx
     */
    private static RequestPostProcessor asUser(String userId, String role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userId,  // principal
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));
        // SecurityMockMvcRequestPostProcessors.authentication() 会把 auth 写入
        // 请求的 SecurityContext 并刷新 SecurityContextHolderAwareFilter，
        // 让 @PreAuthorize 能正常评估。
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
    }

    @BeforeEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ============================================
    // GET /api/users — 列表分页
    // ============================================

    @Test
    @DisplayName("ADMIN 查用户列表：200 + 返回 3 条")
    void listUsers_asAdmin_returns200AndAllRows() throws Exception {
        mockMvc.perform(get("/api/users")
                        .with(asUser("admin-001", "ADMIN"))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .param("pageNum", "1")
                        .param("pageSize", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records.length()").value(3));
    }

    @Test
    @DisplayName("EMPLOYEE 查列表：被 @PreAuthorize 拒绝 → 403")
    void listUsers_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/users")
                        .with(asUser("emp-001", "EMPLOYEE"))
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isForbidden());
    }

    // ============================================
    // GET /api/users/{userId} — 详情
    // ============================================

    @Test
    @DisplayName("ADMIN 查 admin-001 详情：200")
    void getUserDetail_asAdmin_self_returns200() throws Exception {
        mockMvc.perform(get("/api/users/{userId}", "admin-001")
                        .with(asUser("admin-001", "ADMIN"))
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").value("admin-001"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    @DisplayName("查不存在的用户 → body.code=404")
    void getUserDetail_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/users/{userId}", "ghost")
                        .with(asUser("admin-001", "ADMIN"))
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ============================================
    // POST /api/users/{userId}/disable + enable
    // ============================================

    @Test
    @DisplayName("ADMIN 禁用 emp-001：200 + DB 中 status=0")
    void disableUser_asAdmin_succeeds() throws Exception {
        mockMvc.perform(post("/api/users/{userId}/disable", "emp-001")
                        .with(asUser("admin-001", "ADMIN"))
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 验证 DB
        Integer status = jdbc.queryForObject(
                "SELECT status FROM users WHERE user_id = 'emp-001'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(status).isEqualTo(0);
    }

    @Test
    @DisplayName("ADMIN 禁用自己：业务异常 → body.code=400")
    void disableUser_self_returns400() throws Exception {
        mockMvc.perform(post("/api/users/{userId}/disable", "admin-001")
                        .with(asUser("admin-001", "ADMIN"))
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("ADMIN 启用 emp-001（先 disable 再 enable）：200 + DB status=1")
    void enableUser_afterDisable_succeeds() throws Exception {
        // 先禁用
        jdbc.update("UPDATE users SET status = 0 WHERE user_id = 'emp-001'");

        mockMvc.perform(post("/api/users/{userId}/enable", "emp-001")
                        .with(asUser("admin-001", "ADMIN"))
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        Integer status = jdbc.queryForObject(
                "SELECT status FROM users WHERE user_id = 'emp-001'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(status).isEqualTo(1);
    }

    @Test
    @DisplayName("EMPLOYEE 调禁用接口：被 @PreAuthorize 拒绝 → 403")
    void disableUser_asEmployee_returns403() throws Exception {
        mockMvc.perform(post("/api/users/{userId}/disable", "mgr-001")
                        .with(asUser("emp-001", "EMPLOYEE"))
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isForbidden());
    }
}
