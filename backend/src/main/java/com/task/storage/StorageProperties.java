package com.task.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 对象存储配置属性（绑定 {@code storage.*} 配置块）
 *
 * 对应 application.yml：
 * <pre>
 * storage:
 *   type: local              # local | minio | aliyun-oss
 *   local:
 *     path: ./uploads
 *   minio:
 *     endpoint: http://localhost:9000
 *     access-key: ...
 *     secret-key: ...
 *     bucket: task-files
 *   oss:
 *     endpoint: ...
 *     access-key-id: ...
 *     access-key-secret: ...
 *     bucket: ...
 * </pre>
 *
 * 通过 {@code @EnableConfigurationProperties(StorageProperties.class)} 注册，
 * 注入方式：{@code @Autowired StorageProperties storageProperties}
 *
 * @author Mavis
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    /**
     * 存储类型：local / minio / aliyun-oss
     */
    private String type = "local";

    /**
     * 本地存储配置
     */
    private Local local = new Local();

    /**
     * MinIO 存储配置
     */
    private Minio minio = new Minio();

    /**
     * 阿里云 OSS 存储配置
     */
    private Oss oss = new Oss();

    @Data
    public static class Local {
        /**
         * 本地存储根目录（相对或绝对路径），默认 {@code ./uploads}
         */
        private String path = "./uploads";
    }

    @Data
    public static class Minio {
        /**
         * MinIO 服务地址，例如 {@code http://localhost:9000}
         */
        private String endpoint;
        /**
         * MinIO access key
         */
        private String accessKey;
        /**
         * MinIO secret key
         */
        private String secretKey;
        /**
         * 存储桶名称
         */
        private String bucket;
    }

    @Data
    public static class Oss {
        /**
         * 阿里云 OSS Endpoint，例如 {@code https://oss-cn-hangzhou.aliyuncs.com}
         */
        private String endpoint;
        /**
         * 阿里云 OSS AccessKeyId
         */
        private String accessKeyId;
        /**
         * 阿里云 OSS AccessKeySecret
         */
        private String accessKeySecret;
        /**
         * 存储桶名称
         */
        private String bucket;
    }
}
