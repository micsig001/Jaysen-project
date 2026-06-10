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
import java.nio.file.StandardCopyOption;

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
     */
    private Path resolveKeyPath(String key) {
        if (key == null || key.isBlank() || key.contains("..")) {
            // 防止路径穿越攻击：拒绝任何包含 ".." 的 key
            throw BusinessException.badRequest("非法的存储 key: " + key);
        }
        // 统一使用 / 作为路径分隔符
        String normalized = key.replace("\\", "/");
        return resolveRootPath().resolve(normalized).normalize();
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
            // 使用 REPLACE_EXISTING：上传同名文件时覆盖
            long copied = Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
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
        // 本地存储没有真正的"预签名"概念，直接返回普通下载 URL
        // 真正的鉴权由 FileController 在过期时间窗内校验签名 token 实现
        log.info("[存储-Local] 生成本地下载 URL: key={}, expireSeconds={}", key, expireSeconds);
        return "/api/files/" + key + "?expireSeconds=" + expireSeconds;
    }

    @Override
    public boolean exists(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        try {
            Path target = resolveKeyPath(key);
            return Files.exists(target);
        } catch (Exception e) {
            log.warn("[存储-Local] 检查存在性失败: key={}, err={}", key, e.getMessage());
            return false;
        }
    }
}
