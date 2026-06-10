package com.task.storage;

import com.task.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 存储服务工厂（基于 Spring 容器中已注册的 {@link StorageService} Bean）
 *
 * <p>提供两种调用风格：
 * <ul>
 *   <li>{@link #getActive()}  — 拿当前激活的实现（与 {@code @Primary StorageService} 等价，但更明确）</li>
 *   <li>{@link #get(String)}  — 拿指定实现（{@code "localStorageService"} / {@code "minioStorageService"} / {@code "aliyunOssStorageService"}）</li>
 * </ul>
 *
 * <p>设计动机：业务侧若只想"调用当前激活的存储"，直接 {@code @Autowired StorageService} 即可。
 * 但若需要在不同时机切换不同实现（例如一个业务租户用 MinIO，另一个用 OSS），
 * 可以通过本工厂按名获取。
 *
 * @author Mavis
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorageServiceFactory {

    /**
     * Spring 自动按类型注入所有 StorageService 实现；
     * 配合 @Qualifier 按名字解析。
     */
    private final StorageService activeStorageService;
    private final List<StorageService> allStorageServices;

    /**
     * 获取当前激活的存储实现（与 {@code @Primary} 一致）
     */
    public StorageService getActive() {
        return activeStorageService;
    }

    /**
     * 按 bean 名称获取指定实现
     *
     * @param name 实现 bean 名称，支持 {@code "localStorageService"} / {@code "minioStorageService"} / {@code "aliyunOssStorageService"}
     * @return 对应的实现
     * @throws BusinessException 当 {@code name} 找不到或对应 Bean 未注册（说明 storage.type 配置未启用该实现）
     */
    public StorageService get(String name) {
        if (name == null || name.isBlank()) {
            throw BusinessException.badRequest("存储实现名称不能为空");
        }
        for (StorageService svc : allStorageServices) {
            // 通过反射读 bean 名称（也可由实现类实现 NamedStorageService 接口暴露）
            if (matchesBeanName(svc, name)) {
                return svc;
            }
        }
        log.warn("[存储工厂] 未找到实现: name={}, 可用数量={}", name, allStorageServices.size());
        throw BusinessException.notFound("未找到存储实现: " + name);
    }

    /**
     * 列出当前所有已注册的存储实现（供运维/调试使用）
     * <p>key 为实现类的 {@link StorageService#getBeanName()}，与 {@link #get(String)} 一致
     */
    public Map<String, StorageService> listAll() {
        Map<String, StorageService> map = new HashMap<>();
        for (StorageService svc : allStorageServices) {
            map.put(svc.getBeanName(), svc);
        }
        return map;
    }

    /**
     * 判断一个实现是否对应给定的 bean 名称
     */
    private boolean matchesBeanName(StorageService svc, String name) {
        return name != null && name.equals(svc.getBeanName());
    }
}
