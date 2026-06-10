package com.task.storage;

import com.task.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;

/**
 * MinIO 对象存储实现（Stub）
 *
 * <p>当前为占位实现，所有方法抛出 {@link UnsupportedOperationException}，
 * 等待后续引入 {@code minio} SDK（参考 https://min.io/docs/minio/linux/developers/minio-java.html）。
 *
 * <p>作为普通 Java 类实现（无 {@code @Service}），由 {@link StorageConfig} 注入到 Spring 容器
 *
 * <p>引入依赖后的实施步骤（备忘，不在本任务范围内）：
 * <pre>
 * 1) pom.xml: 增加
 *    &lt;dependency&gt;
 *      &lt;groupId&gt;io.minio&lt;/groupId&gt;
 *      &lt;artifactId&gt;minio&lt;/artifactId&gt;
 *      &lt;version&gt;8.5.10&lt;/version&gt;
 *    &lt;/dependency&gt;
 * 2) PostConstruct: 用 StorageProperties.minio.* 构造 MinioClient
 *    MinioClient client = MinioClient.builder()
 *        .endpoint(props.getEndpoint())
 *        .credentials(props.getAccessKey(), props.getSecretKey())
 *        .build();
 * 3) upload: client.putObject(PutObjectArgs.builder()...build())
 * 4) download: client.getObject(GetObjectArgs.builder()...build())
 * 5) presigned: client.getPresignedObjectUrl(...)
 * </pre>
 *
 * @author Mavis
 */
@Slf4j
@RequiredArgsConstructor
public class MinioStorageService implements StorageService {

    private final StorageProperties storageProperties;

    private void notImplemented(String op) {
        log.warn("[存储-MinIO] 操作未实现: op={}, bucket={}", op,
                storageProperties.getMinio().getBucket());
        throw new UnsupportedOperationException(
                "MinIO 存储实现尚未完成，请引入 io.minio:minio SDK 后实现 " + op);
    }

    @Override
    public String upload(String key, InputStream inputStream, String contentType, long size) {
        if (key == null || key.isBlank()) {
            throw BusinessException.badRequest("存储 key 不能为空");
        }
        notImplemented("upload");
        return null; // 不可达
    }

    @Override
    public InputStream download(String key) {
        if (key == null || key.isBlank()) {
            throw BusinessException.badRequest("存储 key 不能为空");
        }
        notImplemented("download");
        return null; // 不可达
    }

    @Override
    public boolean delete(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        notImplemented("delete");
        return false; // 不可达
    }

    @Override
    public String generatePresignedUrl(String key, int expireSeconds) {
        if (key == null || key.isBlank()) {
            throw BusinessException.badRequest("存储 key 不能为空");
        }
        if (expireSeconds <= 0) {
            throw BusinessException.badRequest("过期时间必须大于 0");
        }
        notImplemented("generatePresignedUrl");
        return null; // 不可达
    }

    @Override
    public boolean exists(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        notImplemented("exists");
        return false; // 不可达
    }
}
