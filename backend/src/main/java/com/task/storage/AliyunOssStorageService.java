package com.task.storage;

import com.task.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;

/**
 * 阿里云 OSS 对象存储实现（Stub）
 *
 * <p>当前为占位实现，所有方法抛出 {@link UnsupportedOperationException}，
 * 等待后续引入阿里云 OSS Java SDK（参考 https://help.aliyun.com/document_detail/32008.html）。
 *
 * <p>作为普通 Java 类实现（无 {@code @Service}），由 {@link StorageConfig} 注入到 Spring 容器
 *
 * <p>引入依赖后的实施步骤（备忘，不在本任务范围内）：
 * <pre>
 * 1) pom.xml: 增加
 *    &lt;dependency&gt;
 *      &lt;groupId&gt;com.aliyun.oss&lt;/groupId&gt;
 *      &lt;artifactId&gt;aliyun-sdk-oss&lt;/artifactId&gt;
 *      &lt;version&gt;3.17.4&lt;/version&gt;
 *    &lt;/dependency&gt;
 * 2) PostConstruct: 用 StorageProperties.oss.* 构造 OSS 客户端
 *    OSS ossClient = new OSSClientBuilder()
 *        .build(props.getEndpoint(), props.getAccessKeyId(), props.getAccessKeySecret());
 * 3) upload: ossClient.putObject(bucket, key, inputStream)
 * 4) download: ossClient.getObject(bucket, key).getObjectContent()
 * 5) presigned: ossClient.generatePresignedUrl(bucket, key, expireDate)
 * 6) exists: ossClient.doesObjectExist(bucket, key)
 * </pre>
 *
 * @author Mavis
 */
@Slf4j
@RequiredArgsConstructor
public class AliyunOssStorageService implements StorageService {

    private final StorageProperties storageProperties;

    private void notImplemented(String op) {
        log.warn("[存储-OSS] 操作未实现: op={}, bucket={}", op,
                storageProperties.getOss().getBucket());
        throw new UnsupportedOperationException(
                "阿里云 OSS 存储实现尚未完成，请引入 com.aliyun.oss:aliyun-sdk-oss 后实现 " + op);
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
