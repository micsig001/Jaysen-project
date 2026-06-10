package com.task.archive;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 归档配置属性
 *
 * 绑定 application.yml 中的 archive.* 配置项
 * 提供配置化的归档参数，方便不同环境调整
 *
 * @author Mavis
 */
@Data
@Component
@ConfigurationProperties(prefix = "archive")
public class ArchiveProperties {

    /**
     * 是否启用归档功能
     * true：定时任务和手动触发都生效
     * false：归档相关接口返回"功能未启用"
     */
    private Boolean enabled = true;

    /**
     * 定时任务 Cron 表达式
     * 默认：每月1号凌晨3点（0 0 3 1 * ?）
     */
    private String cron = "0 0 3 1 * ?";

    /**
     * 单批归档任务数
     * 默认1000条/批，避免长时间锁表
     */
    private Integer batchSize = 1000;

    /**
     * 归档时间阈值（月）
     * 任务创建时间超过该月数且状态为终态才会被归档
     * 默认12个月（1年）
     */
    private Integer intervalMonths = 12;

    /**
     * 单次归档最长执行时间（分钟）
     * 超过该时间自动停止当前批次，防止长跑阻塞
     * 默认180分钟（3小时）
     */
    private Integer maxRuntimeMinutes = 180;

    /**
     * Redis 分布式锁 Key
     * 多实例部署时防止重复执行
     */
    private String lockKey = "task:archive:lock";

    /**
     * 分布式锁过期时间（秒）
     * 兜底机制，防止服务异常导致锁永不释放
     */
    private Long lockExpireSeconds = 3600L;
}
