package com.task.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 对象存储动态 Bean 配置
 *
 * <p>根据 {@code storage.type} 配置决定激活哪个 {@link StorageService} 实现，并将其标记为
 * {@code @Primary}，使得业务代码可以直接 {@code @Autowired StorageService storageService}
 * 而无需 {@code @Qualifier}。
 *
 * <p>三种实现并存于类路径，但只有匹配 type 的那个会被注册为 Spring Bean。
 * 业务侧仍然可以通过 {@code @Qualifier("localStorageService")} 等显式注入特定实现。
 *
 * <p>当前 type 取值：
 * <ul>
 *   <li>{@code local}      — 本地磁盘（默认，{@code matchIfMissing=true}）</li>
 *   <li>{@code minio}      — MinIO 对象存储</li>
 *   <li>{@code aliyun-oss} — 阿里云 OSS</li>
 * </ul>
 *
 * <p>若 {@code storage.type} 被配置为不支持的值（如 {@code s3}），由于以上三个条件都不匹配，
 * 容器里就不会有任何 {@code StorageService} Bean，业务侧注入时立刻收到
 * {@code NoSuchBeanDefinitionException}，可据此快速定位配置错误。
 *
 * @author Mavis
 */
@Slf4j
@Configuration
public class StorageConfig {

    /**
     * 本地存储实现 Bean
     *
     * <p>条件：{@code storage.type=local}（未配置时默认走 local，{@code matchIfMissing=true}）
     * 标记 {@code @Primary}：业务侧直接注入 {@code StorageService} 时拿到本地实现
     */
    @Bean(name = "localStorageService")
    @Primary
    @ConditionalOnProperty(prefix = "storage", name = "type", havingValue = "local", matchIfMissing = true)
    public StorageService localStorageService(StorageProperties storageProperties,
                                              SignedUrlUtil signedUrlUtil) {
        log.info("[存储配置] 激活 Local 存储实现, path={}",
                storageProperties.getLocal().getPath());
        return new LocalStorageService(storageProperties, signedUrlUtil);
    }

    /**
     * MinIO 存储实现 Bean
     *
     * <p>条件：{@code storage.type=minio}
     * <p>不标 {@code @Primary}：只让 localStorageService 是 primary
     */
    @Bean(name = "minioStorageService")
    @ConditionalOnProperty(prefix = "storage", name = "type", havingValue = "minio")
    public StorageService minioStorageService(StorageProperties storageProperties) {
        log.info("[存储配置] 激活 MinIO 存储实现, endpoint={}, bucket={}",
                storageProperties.getMinio().getEndpoint(),
                storageProperties.getMinio().getBucket());
        return new MinioStorageService(storageProperties);
    }

    /**
     * 阿里云 OSS 存储实现 Bean
     *
     * <p>条件：{@code storage.type=aliyun-oss}
     * <p>不标 {@code @Primary}：只让 localStorageService 是 primary
     */
    @Bean(name = "aliyunOssStorageService")
    @ConditionalOnProperty(prefix = "storage", name = "type", havingValue = "aliyun-oss")
    public StorageService aliyunOssStorageService(StorageProperties storageProperties) {
        log.info("[存储配置] 激活 Aliyun OSS 存储实现, endpoint={}, bucket={}",
                storageProperties.getOss().getEndpoint(),
                storageProperties.getOss().getBucket());
        return new AliyunOssStorageService(storageProperties);
    }
}
