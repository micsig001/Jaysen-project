package com.task.wework;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 假的企业微信 API 客户端（用于本地/测试环境端到端跑通 OAuth 流程）
 *
 * <p>由 {@link WeWorkApiConfig} 在 profile 为 {@code dev} 或 {@code test} 时注册为
 * {@code @Primary} Bean，覆盖真实 {@link WeWorkApiClient}。
 *
 * <p>行为：
 * <ul>
 *   <li>access token 固定返回 "FAKE_ACCESS_TOKEN"（不需要网络）</li>
 *   <li>{@link #getUserInfoByCode(String)} 按 code 解析 userId：
 *       code 形如 {@code "valid_xxx"} 时返回对应 userId；其它返回 null</li>
 *   <li>{@link #getUserDetail(String)} 返回稳定的假用户详情 JSON</li>
 *   <li>消息发送、预签名等操作只记录日志，返回成功</li>
 * </ul>
 *
 * <p>支持调用方在测试中通过 {@link #seedUser(String, String, String, String)} 预置用户数据，
 * 或通过 {@link #registerCode(String, String)} 将 code 显式映射到 userId。
 *
 * @author Mavis
 */
@Slf4j
public class FakeWeWorkApiClient extends WeWorkApiClient {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** code -> userId 映射（预置或运行时注册） */
    private final Map<String, String> codeToUserId = new ConcurrentHashMap<>();
    /** userId -> 用户详情覆盖（用于自定义测试场景） */
    private final Map<String, ObjectNode> userDetailOverrides = new ConcurrentHashMap<>();

    /**
     * 默认构造器（Fake 不需要任何外部依赖）
     * <p>父类的 {@code @Value} 字段在 Fake 场景下不会被注入，本类也不使用；
     * Redis/WebClient 也不需要。
     */
    public FakeWeWorkApiClient() {
        super(null, null, null);
        log.warn("[WeWork-Fake] 初始化 FakeWeWorkApiClient —— 已覆盖真实 WeWorkApiClient，"
                + "OAuth 流程将走假数据，不会调用企微官方接口");
    }

    /**
     * 预置一个 code -> userId 的映射
     */
    public FakeWeWorkApiClient registerCode(String code, String userId) {
        codeToUserId.put(code, userId);
        return this;
    }

    /**
     * 预置一个 userId -> 用户详情的完整 JSON
     */
    public FakeWeWorkApiClient seedUser(String userId, String name, String mobile, String email) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("userid", userId);
        node.put("name", name != null ? name : userId);
        if (mobile != null) node.put("mobile", mobile);
        if (email != null) node.put("email", email);
        node.put("avatar", "https://fake-wework.example/avatars/" + userId + ".png");
        ArrayNode dept = node.putArray("department");
        dept.add(1L);
        userDetailOverrides.put(userId, node);
        return this;
    }

    // ==================== 重写父类方法 ====================

    @Override
    public String getAccessToken() {
        log.debug("[WeWork-Fake] getAccessToken → 固定返回 FAKE_ACCESS_TOKEN");
        return "FAKE_ACCESS_TOKEN";
    }

    @Override
    public JsonNode getUserInfoByCode(String code) {
        if (code == null || code.isBlank()) {
            log.warn("[WeWork-Fake] getUserInfoByCode: 空 code，返回 null");
            return null;
        }
        // code 显式映射优先
        String mapped = codeToUserId.get(code);
        if (mapped != null) {
            return buildUserInfoResponse(mapped);
        }
        // 兜底：code 形如 valid_<userId> 时直接解析
        if (code.startsWith("valid_")) {
            String userId = code.substring("valid_".length());
            return buildUserInfoResponse(userId);
        }
        // 其它任何 code 都被视为无效
        log.warn("[WeWork-Fake] getUserInfoByCode: 无法识别的 code={}", code);
        return null;
    }

    @Override
    public JsonNode getUserDetail(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        ObjectNode override = userDetailOverrides.get(userId);
        if (override != null) {
            return override.deepCopy();
        }
        // 兜底：返回稳定假数据
        ObjectNode node = objectMapper.createObjectNode();
        node.put("userid", userId);
        node.put("name", "FakeUser-" + userId);
        node.put("mobile", "13800000000");
        node.put("email", userId + "@fake-wework.example");
        node.put("avatar", "https://fake-wework.example/avatars/" + userId + ".png");
        ArrayNode dept = node.putArray("department");
        dept.add(1L);
        return node;
    }

    @Override
    public JsonNode getDepartmentList(Long departmentId) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("errcode", 0);
        root.put("errmsg", "ok");
        ArrayNode arr = root.putArray("department");
        ObjectNode dept = objectMapper.createObjectNode();
        dept.put("id", 1L);
        dept.put("name", "默认部门");
        dept.put("parentid", 0L);
        arr.add(dept);
        return root;
    }

    @Override
    public JsonNode getDepartmentUsers(Long departmentId, Boolean fetchChild) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("errcode", 0);
        root.put("errmsg", "ok");
        ArrayNode arr = root.putArray("userlist");
        // 返回一个稳定假用户
        ObjectNode u = objectMapper.createObjectNode();
        u.put("userid", "fake_dept_user");
        u.put("name", "部门假用户");
        ArrayNode uDept = u.putArray("department");
        uDept.add(1L);
        arr.add(u);
        return root;
    }

    @Override
    public boolean sendMessage(Map<String, Object> messageBody) {
        log.info("[WeWork-Fake] sendMessage touser={} type={} — 假成功",
                messageBody.get("touser"), messageBody.get("msgtype"));
        return true;
    }

    @Override
    public Map<String, Object> buildTextMessage(String toUser, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("touser", toUser);
        message.put("msgtype", "text");
        message.put("agentid", 0);
        Map<String, String> text = new LinkedHashMap<>();
        text.put("content", content);
        message.put("text", text);
        return message;
    }

    @Override
    public Map<String, Object> buildCardMessage(String toUser, String title, String description,
                                                 String url, String buttonText) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("touser", toUser);
        message.put("msgtype", "textcard");
        message.put("agentid", 0);
        Map<String, String> card = new LinkedHashMap<>();
        card.put("title", title);
        card.put("description", description);
        card.put("url", url);
        card.put("btntxt", buttonText != null ? buttonText : "查看详情");
        message.put("textcard", card);
        return message;
    }

    @Override
    public Map<String, Object> buildTodoMessage(String toUser, String title, String description) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("touser", toUser);
        message.put("msgtype", "template_card");
        message.put("agentid", 0);
        Map<String, Object> templateCard = new LinkedHashMap<>();
        templateCard.put("card_type", "todo");
        Map<String, Object> todo = new LinkedHashMap<>();
        todo.put("title", title);
        todo.put("description", description);
        templateCard.put("todo", todo);
        message.put("template_card", templateCard);
        return message;
    }

    // ==================== 内部辅助 ====================

    /**
     * 构造 getUserInfoByCode 接口的成功响应 JSON
     * <p>对应企微 {@code /cgi-bin/auth/getuserinfo} 返回格式
     */
    private JsonNode buildUserInfoResponse(String userId) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("errcode", 0);
        root.put("errmsg", "ok");
        root.put("UserId", userId);
        root.put("OpenId", "fake_openid_" + UUID.randomUUID());
        root.put("DeviceId", "fake_device_" + System.currentTimeMillis());
        return root;
    }
}
