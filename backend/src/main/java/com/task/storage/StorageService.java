package com.task.storage;

import java.io.InputStream;

/**
 * 对象存储策略接口（Strategy Pattern）
 *
 * 支持的实现：
 *   - local        本地磁盘存储
 *   - minio        MinIO 对象存储
 *   - aliyun-oss   阿里云 OSS
 *
 * 选哪个由 {@code storage.type} 配置决定，详见 {@link com.task.storage.StorageConfig}。
 *
 * 注意事项：
 *   1) 所有方法都是阻塞 IO，调用方需自行控制并发与超时
 *   2) {@code key} 统一使用相对路径形式，例如 {@code "tasks/123/attachments/uuid.pdf"}
 *   3) 上传成功后返回的是可被前端访问的 URL（不一定是 key 本身）
 *   4) 预签名 URL 仅在支持对象存储的实现中有效；本地存储可能直接返回普通下载 URL
 *
 * @author Mavis
 */
public interface StorageService {

    /**
     * 暴露对应的 Spring Bean 名称
     * <p>实现类必须返回在 {@link StorageConfig} 中注册的 bean 名称
     * （{@code "localStorageService"} / {@code "minioStorageService"} / {@code "aliyunOssStorageService"}），
     * 供 {@link StorageServiceFactory} 按名查找。
     */
    String getBeanName();

    /**
     * 上传文件
     *
     * @param key          存储 key（相对路径），如 {@code "tasks/123/attachments/uuid.pdf"}
     * @param inputStream  文件输入流（由调用方负责关闭，或由实现内部关闭）
     * @param contentType  MIME 类型，允许为 null
     * @param size         文件大小（字节），用于预分配 / 校验；0 表示未知
     * @return 存储后的可访问 URL（前端可直接 GET）
     * @throws com.task.common.BusinessException 上传失败时
     */
    String upload(String key, InputStream inputStream, String contentType, long size);

    /**
     * 下载文件
     *
     * @param key 存储 key
     * @return 文件输入流，调用方负责关闭
     * @throws com.task.common.BusinessException 文件不存在或下载失败时
     */
    InputStream download(String key);

    /**
     * 删除文件
     *
     * @param key 存储 key
     * @return true=成功；false=文件不存在或删除失败
     */
    boolean delete(String key);

    /**
     * 生成预签名 URL（用于前端直传 / 分享链接 / 临时下载）
     *
     * @param key           存储 key
     * @param expireSeconds 过期秒数（必须 &gt; 0）
     * @return 临时访问 URL
     * @throws com.task.common.BusinessException 生成失败时
     */
    String generatePresignedUrl(String key, int expireSeconds);

    /**
     * 检查文件是否存在
     *
     * @param key 存储 key
     * @return true=存在；false=不存在
     */
    boolean exists(String key);
}
