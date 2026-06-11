package com.task.storage;

import com.task.common.BusinessException;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 对象存储实现
 *
 * <p>使用官方 {@code io.minio:minio} SDK（8.5.10），通过 {@link StorageProperties#getMinio()} 配置连接。
 *
 * <p>关键设计：
 * <ul>
 *   <li>{@code MinioClient} 在 {@link #init()} 阶段（{@code @PostConstruct}）构造，业务方法
 *       共享同一实例（MinioClient 内部已带 OkHttp 连接池）</li>
 *   <li>上传后返回的 URL 形如 {@code http://<endpoint>/<bucket>/<key>}，
 *       前端可直接 GET（前提是 bucket 设了 public-read，或 URL 是预签名）</li>
 *   <li>预签名 URL 使用 {@link Method#GET}，默认指向当前 MinioClient 的 endpoint
 *       （如有反向代理/Nginx，可通过 {@code storage.minio.endpoint} 改写）</li>
 *   <li>下载流不关闭：调用方负责关闭（{@link GetObjectResponse} 是 {@code FilterInputStream}，
 *       关闭时连带关闭底层 socket）</li>
 * </ul>
 *
 * <p>注册方式：{@link StorageConfig#minioStorageService(StorageProperties)} 在
 * {@code storage.type=minio} 时激活为 Spring Bean（无 {@code @Primary}，由 local 优先）。
 *
 * @author Mavis
 */
@Slf4j
@RequiredArgsConstructor
public class MinioStorageService implements StorageService {

    private final StorageProperties storageProperties;

    /**
     * MinIO 客户端（@PostConstruct 阶段构造）
     */
    private MinioClient minioClient;

    @Override
    public String getBeanName() {
        return "minioStorageService";
    }

    /**
     * 构造 MinioClient：endpoint + accessKey + secretKey
     * <p>如果配置缺失（endpoint / accessKey / secretKey 任一为空），降级为 lazy 模式：
     * 第一次业务方法调用时再报错，而不是启动失败。
     */
    @PostConstruct
    public void init() {
        StorageProperties.Minio cfg = storageProperties.getMinio();
        if (cfg.getEndpoint() == null || cfg.getEndpoint().isBlank()
                || cfg.getAccessKey() == null || cfg.getAccessKey().isBlank()
                || cfg.getSecretKey() == null || cfg.getSecretKey().isBlank()) {
            log.warn("[存储-MinIO] storage.minio.* 配置不完整（endpoint/accessKey/secretKey 必填），"
                    + "MinioClient 将在首次业务调用时延迟初始化失败。当前 bucket={}", cfg.getBucket());
            return;
        }
        try {
            this.minioClient = MinioClient.builder()
                    .endpoint(cfg.getEndpoint())
                    .credentials(cfg.getAccessKey(), cfg.getSecretKey())
                    .build();
            log.info("[存储-MinIO] MinioClient 初始化成功: endpoint={}, bucket={}",
                    cfg.getEndpoint(), cfg.getBucket());
        } catch (Exception e) {
            // 不抛异常：避免在缺少配置时让 Spring 容器启动失败
            // 业务方法会捕获 NPE 并给出友好错误
            log.error("[存储-MinIO] MinioClient 初始化失败: endpoint={}", cfg.getEndpoint(), e);
            this.minioClient = null;
        }
    }

    private void ensureClient() {
        if (minioClient == null) {
            throw BusinessException.status(503, "MinIO 客户端未初始化，请检查 storage.minio.* 配置");
        }
        if (storageProperties.getMinio().getBucket() == null
                || storageProperties.getMinio().getBucket().isBlank()) {
            throw BusinessException.badRequest("MinIO bucket 未配置 (storage.minio.bucket)");
        }
    }

    // ==================== 接口方法 ====================

    @Override
    public String upload(String key, InputStream inputStream, String contentType, long size) {
        if (key == null || key.isBlank()) {
            throw BusinessException.badRequest("存储 key 不能为空");
        }
        if (inputStream == null) {
            throw BusinessException.badRequest("文件输入流不能为空");
        }
        ensureClient();
        String bucket = storageProperties.getMinio().getBucket();

        try {
            // MinIO SDK 要求：已知 size 时 partSize 可以为 -1（自动算），
            // 未知 size（-1L）时 partSize 必须 > 0
            // 这里统一给一个安全的 partSize (5 MiB)，与 SDK 内部默认值一致
            long partSize = -1L;
            if (size <= 0) {
                partSize = 5L * 1024 * 1024; // 5 MiB
            }
            PutObjectArgs args = PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(inputStream, size, partSize)
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .build();
            minioClient.putObject(args);
            log.info("[存储-MinIO] 上传成功: bucket={}, key={}, size={}", bucket, key, size);
            return publicUrl(key);
        } catch (Exception e) {
            log.error("[存储-MinIO] 上传失败: bucket={}, key={}", bucket, key, e);
            throw new BusinessException(500, "MinIO 上传失败: " + e.getMessage());
        }
    }

    @Override
    public InputStream download(String key) {
        if (key == null || key.isBlank()) {
            throw BusinessException.badRequest("存储 key 不能为空");
        }
        ensureClient();
        String bucket = storageProperties.getMinio().getBucket();

        try {
            GetObjectArgs args = GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build();
            GetObjectResponse response = minioClient.getObject(args);
            log.info("[存储-MinIO] 下载流已返回: bucket={}, key={}（调用方负责关闭）", bucket, key);
            return response;
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                log.warn("[存储-MinIO] 文件不存在: bucket={}, key={}", bucket, key);
                throw BusinessException.notFound("文件不存在: " + key);
            }
            log.error("[存储-MinIO] 下载失败: bucket={}, key={}", bucket, key, e);
            throw new BusinessException(500, "MinIO 下载失败: " + e.errorResponse().message());
        } catch (Exception e) {
            log.error("[存储-MinIO] 下载失败: bucket={}, key={}", bucket, key, e);
            throw new BusinessException(500, "MinIO 下载失败: " + e.getMessage());
        }
    }

    @Override
    public boolean delete(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        ensureClient();
        String bucket = storageProperties.getMinio().getBucket();

        try {
            RemoveObjectArgs args = RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build();
            minioClient.removeObject(args);
            log.info("[存储-MinIO] 删除成功: bucket={}, key={}", bucket, key);
            return true;
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                log.warn("[存储-MinIO] 删除跳过（文件不存在）: bucket={}, key={}", bucket, key);
                return false;
            }
            log.error("[存储-MinIO] 删除失败: bucket={}, key={}", bucket, key, e);
            return false;
        } catch (Exception e) {
            log.error("[存储-MinIO] 删除失败: bucket={}, key={}", bucket, key, e);
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
        // MinIO SDK 限制：expiry 最大 7 天（604800 秒），超出截断并 warn
        int expiry = Math.min(expireSeconds, (int) TimeUnit.DAYS.toSeconds(7));
        if (expiry < expireSeconds) {
            log.warn("[存储-MinIO] 预签名 URL expireSeconds={} 超过 7 天上限，截断为 {}",
                    expireSeconds, expiry);
        }
        ensureClient();
        String bucket = storageProperties.getMinio().getBucket();

        try {
            GetPresignedObjectUrlArgs args = GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(key)
                    .expiry(expiry)
                    .build();
            String url = minioClient.getPresignedObjectUrl(args);
            log.info("[存储-MinIO] 预签名 URL 已生成: bucket={}, key={}, expireSeconds={}",
                    bucket, key, expiry);
            return url;
        } catch (Exception e) {
            log.error("[存储-MinIO] 预签名 URL 生成失败: bucket={}, key={}", bucket, key, e);
            throw new BusinessException(500, "MinIO 预签名 URL 生成失败: " + e.getMessage());
        }
    }

    @Override
    public boolean exists(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        ensureClient();
        String bucket = storageProperties.getMinio().getBucket();

        try {
            StatObjectArgs args = StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build();
            StatObjectResponse response = minioClient.statObject(args);
            return response != null && response.size() >= 0;
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return false;
            }
            log.error("[存储-MinIO] exists 检查失败: bucket={}, key={}", bucket, key, e);
            return false;
        } catch (Exception e) {
            log.error("[存储-MinIO] exists 检查失败: bucket={}, key={}", bucket, key, e);
            return false;
        }
    }

    // ==================== 辅助 ====================

    /**
     * 构造对象公开访问 URL（无签名），形如 {@code <endpoint>/<bucket>/<key>}
     * <p>仅当 bucket 配置了 public-read 访问策略时才有效；否则前端需要走预签名 URL。
     */
    private String publicUrl(String key) {
        String endpoint = storageProperties.getMinio().getEndpoint();
        String bucket = storageProperties.getMinio().getBucket();
        // 去掉 endpoint 末尾的 /，key 开头的 /，保证 URL 拼接干净
        String base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        String path = key.startsWith("/") ? key.substring(1) : key;
        return base + "/" + bucket + "/" + path;
    }
}
