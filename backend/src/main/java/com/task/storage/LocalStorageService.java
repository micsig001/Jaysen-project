package com.task.storage;

import com.task.common.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


/**
 * 本地磁盘存储实现
 *
 * <p>特点：
 * <ul>
 *   <li>作为普通 Java 类实现（无 {@code @Service}），由 {@link StorageConfig} 注入到 Spring 容器
 *   <li>简单实现：单文件 IO，不分片、不断点续传
 *   <li>上传成功后返回的 URL 形如 {@code /api/files/{key}}，
 *       前端通过后端 Controller 代理下载（避免直接暴露磁盘路径）
 * </ul>
 *
 * <p>后续可以新增 {@code FileController} 提供 {@code GET /api/files/**} 代理接口。
 *
 * @author Mavis
 */
@Slf4j
@RequiredArgsConstructor
public class LocalStorageService implements StorageService {

    private final StorageProperties storageProperties;
    private final SignedUrlUtil signedUrlUtil;

    @Override
    public String getBeanName() {
        return "localStorageService";
    }

    /**
     * 应用启动时确保本地目录存在
     */
    @PostConstruct
    public void init() {
        Path root = resolveRootPath();
        try {
            if (!Files.exists(root)) {
                Files.createDirectories(root);
                log.info("[存储-Local] 已创建本地存储目录: {}", root.toAbsolutePath());
            } else {
                log.info("[存储-Local] 本地存储目录已存在: {}", root.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("[存储-Local] 创建本地存储目录失败: {}", root.toAbsolutePath(), e);
            throw new IllegalStateException("无法创建本地存储目录: " + root, e);
        }
    }

    /**
     * 解析配置文件中的 path 为绝对 Path
     */
    private Path resolveRootPath() {
        String configured = storageProperties.getLocal().getPath();
        if (configured == null || configured.isBlank()) {
            configured = "./uploads";
        }
        Path p = Paths.get(configured);
        return p.isAbsolute() ? p : p.toAbsolutePath();
    }

    /**
     * 根据 key 解析出磁盘上的目标文件路径
     *
     * <p>安全校验（防路径穿越）：
     * <ul>
     *   <li>key 必须非空</li>
     *   <li>拒绝包含 ".." 的相对路径穿越</li>
     *   <li>拒绝以 "/" 或 "\" 开头的绝对路径（Path.resolve(absolute) 会替换 base）</li>
     *   <li>normalize 后必须仍以 root 开头（兜底校验）</li>
     * </ul>
     */
    private Path resolveKeyPath(String key) {
        if (key == null || key.isBlank()) {
            throw BusinessException.badRequest("存储 key 不能为空");
        }
        if (key.contains("..")) {
            throw BusinessException.badRequest("非法的存储 key: " + key);
        }
        if (key.startsWith("/") || key.startsWith("\\")) {
            throw BusinessException.badRequest("非法的存储 key: " + key);
        }
        // 统一使用 / 作为路径分隔符
        String normalized = key.replace("\\", "/");
        Path root = resolveRootPath().normalize();
        Path resolved = root.resolve(normalized).normalize();
        // 双重校验：normalize 后必须仍以 root 开头
        if (!resolved.startsWith(root)) {
            throw BusinessException.badRequest("非法的存储 key: " + key);
        }
        return resolved;
    }

    @Override
    public String upload(String key, InputStream inputStream, String contentType, long size) {
        if (key == null || key.isBlank()) {
            throw BusinessException.badRequest("存储 key 不能为空");
        }
        if (inputStream == null) {
            throw BusinessException.badRequest("文件输入流不能为空");
        }
        Path target = resolveKeyPath(key);
        Path parent = target.getParent();
        try {
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            // 修复（P1-3）：拒绝覆盖已存在的文件，避免误操作丢失数据
            // 调用方应在 key 中拼接 UUID 防止冲突
            if (Files.exists(target)) {
                log.warn("[存储-Local] 上传目标已存在，拒绝覆盖: key={}", key);
                throw new BusinessException(409, "目标文件已存在: " + key);
            }
            long copied = Files.copy(inputStream, target);
            if (size > 0 && copied != size) {
                log.warn("[存储-Local] 上传字节数不匹配: expected={}, actual={}, key={}", size, copied, key);
            }
            log.info("[存储-Local] 上传成功: key={}, size={} bytes, path={}", key, copied, target);
            // 返回前端可访问的相对 URL（由 FileController 代理）
            return "/api/files/" + key;
        } catch (IOException e) {
            log.error("[存储-Local] 上传失败: key={}", key, e);
            throw new BusinessException(500, "文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public InputStream download(String key) {
        if (key == null || key.isBlank()) {
            throw BusinessException.badRequest("存储 key 不能为空");
        }
        Path target = resolveKeyPath(key);
        if (!Files.exists(target)) {
            throw BusinessException.notFound("文件不存在: " + key);
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException e) {
            log.error("[存储-Local] 下载失败: key={}", key, e);
            throw new BusinessException(500, "文件下载失败: " + e.getMessage());
        }
    }

    @Override
    public boolean delete(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        Path target = resolveKeyPath(key);
        try {
            boolean deleted = Files.deleteIfExists(target);
            if (deleted) {
                log.info("[存储-Local] 删除成功: key={}", key);
            } else {
                log.warn("[存储-Local] 删除跳过（文件不存在）: key={}", key);
            }
            return deleted;
        } catch (IOException e) {
            log.error("[存储-Local] 删除失败: key={}", key, e);
            return false;
        }
    }

    @Override
    public String generatePresignedUrl(String key, int expireSeconds) {
        if (key == null || key.isBlank()) {
            throw BusinessException.badRequest("存储 key 不能为空");
        }
        if (expireSeconds <= 0) {
            throw BusinessException.badRequest("过期时间必须大于 0");
        }
        // 修复（P0-2）：使用 SignedUrlUtil 生成 HMAC-SHA256 签名 URL
        // FileController 收到请求时校验签名 + 过期时间，防止未授权下载
        log.info("[存储-Local] 生成本地下载 URL: key={}, expireSeconds={}", key, expireSeconds);
        return signedUrlUtil.generate(key, expireSeconds);
    }

    @Override
    public boolean exists(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        try {
            Path target = resolveKeyPath(key);
            return Files.exists(target);
        } catch (BusinessException e) {
            // 非法的 key（路径穿越等）— 静默返回 false，避免泄露校验细节
            log.warn("[存储-Local] exists 检测到非法 key: key={}, err={}", key, e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("[存储-Local] 检查存在性失败: key={}", key, e);
            return false;
        }
    }
}
