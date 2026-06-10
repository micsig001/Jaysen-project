package com.task.storage;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 文件下载签名工具（HMAC-SHA256）
 *
 * <p>用于为本地存储的文件生成带过期时间的下载 URL，格式：
 * <pre>
 *   /api/files/{encodedKey}?exp={expireSeconds}&sig={base64url(hmac)}
 * </pre>
 *
 * <p>签名字符串：{@code HMAC-SHA256(secret, key + ":" + exp)}，再用 base64url 编码。
 *
 * <p>安全要点：
 * <ul>
 *   <li>使用恒定时间比较（{@link MessageDigest#isEqual}）防止时序攻击</li>
 *   <li>校验 exp 必须 > 当前时间（防止过期 URL 被重放）</li>
 *   <li>secret 长度 &gt;= 32 字符，启动时强校验</li>
 * </ul>
 *
 * <p>secret 优先读取 {@code app.file-download-secret}，否则 fallback 到
 * {@code JWT_SECRET} 环境变量；生产环境应配置独立 secret。
 *
 * @author Mavis
 */
@Slf4j
@Component
public class SignedUrlUtil {

    /**
     * 签名密钥（≥32 字符）
     * 优先使用 app.file-download-secret；否则复用 JWT_SECRET
     */
    private final String secret;

    public SignedUrlUtil(
            @Value("${app.file-download-secret:}") String fileDownloadSecret,
            @Value("${jwt.secret:}") String jwtSecret
    ) {
        // 优先 file-download-secret；fallback 到 JWT_SECRET
        String chosen = (fileDownloadSecret != null && !fileDownloadSecret.isBlank())
                ? fileDownloadSecret
                : jwtSecret;
        if (chosen == null || chosen.isBlank()) {
            throw new IllegalStateException(
                    "签名密钥未配置：请设置环境变量 FILE_DOWNLOAD_SECRET 或 JWT_SECRET");
        }
        if (chosen.length() < 32) {
            throw new IllegalStateException(
                    "签名密钥长度必须 >= 32 字符，当前 " + chosen.length());
        }
        this.secret = chosen;
    }

    @PostConstruct
    void logSecretFingerprint() {
        // 不打印密钥，只打印长度和前 4 字符指纹（便于运维确认配置）
        log.info("[文件签名] SignedUrlUtil 初始化完成, secretLength={}, prefix={}***",
                secret.length(), secret.substring(0, 4));
    }

    /**
     * 生成带签名的下载 URL
     *
     * @param key           存储 key
     * @param expireSeconds 过期秒数（> 0）
     * @return 形如 {@code /api/files/{encodedKey}?exp=...&sig=...}
     */
    public String generate(String key, int expireSeconds) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key 不能为空");
        }
        if (expireSeconds <= 0) {
            throw new IllegalArgumentException("expireSeconds 必须 > 0");
        }
        long exp = System.currentTimeMillis() / 1000L + expireSeconds;
        String sig = sign(key, exp);
        return "/api/files/" + encodePath(key) + "?exp=" + exp + "&sig=" + sig;
    }

    /**
     * 校验签名
     *
     * @param key 存储 key（已经过 URL 解码）
     * @param exp URL 中的 exp（秒）
     * @param sig URL 中的 sig（base64url）
     * @return true=合法且未过期；false=不合法或已过期
     */
    public boolean verify(String key, long exp, String sig) {
        if (key == null || key.isBlank() || sig == null || sig.isBlank()) {
            return false;
        }
        long now = System.currentTimeMillis() / 1000L;
        if (now > exp) {
            log.debug("[文件签名] URL 已过期: key={}, exp={}, now={}", key, exp, now);
            return false;
        }
        String expected = sign(key, exp);
        boolean ok = constantTimeEquals(expected, sig);
        if (!ok) {
            log.warn("[文件签名] 签名校验失败: key={}, exp={}", key, exp);
        }
        return ok;
    }

    /**
     * HMAC-SHA256(key + ":" + exp) → base64url
     */
    private String sign(String key, long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hmac = mac.doFinal((key + ":" + exp).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 签名失败", e);
        }
    }

    /**
     * 恒定时间比较，防止时序攻击
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] ab = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(ab, bb);
    }

    /**
     * URL 编码 key（保留 / 让路径可读，编码特殊字符）
     */
    private String encodePath(String key) {
        // 用 URLEncoder 编码（会把 / 也编码为 %2F），避免路径解析歧义
        return URLEncoder.encode(key, StandardCharsets.UTF_8);
    }

    /**
     * URL 解码 key（FileController 收到请求后调用）
     */
    public String decodePath(String encoded) {
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }
}
