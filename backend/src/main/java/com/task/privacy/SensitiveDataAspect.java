package com.task.privacy;

import com.task.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 敏感数据脱敏切面
 *
 * 拦截 Controller 返回的 Result，扫描 data 中所有标了 @SensitiveData 的字段
 * 递归处理嵌套对象和集合
 *
 * 权限豁免：
 *   - 超级管理员（ADMIN）：默认可见明文（除非字段上 maskForAdmin=true）
 *   - 用户本人：可见自己的明文（基于 userId 字段匹配）
 *
 * 开关：application.yml 中 sensitive-data.enabled=false 时全跳过
 *
 * @author Mavis
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class SensitiveDataAspect {

    private final DesensitizationUtil desensitizationUtil;
    private final SensitiveDataProperties properties;

    @Around("execution(* com.task..controller..*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        // 功能开关
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            return pjp.proceed();
        }

        Object result = pjp.proceed();
        if (result == null) {
            return null;
        }

        // 当前用户角色
        boolean isAdmin = isCurrentUserAdmin();
        String currentUserId = currentUserId();

        // 扫描并脱敏
        try {
            processResult(result, isAdmin, currentUserId);
        } catch (Exception e) {
            // 修复（B3）：脱敏失败时安全降级，绝不能返回明文
            // 策略：把 data 置空 + 返回错误响应，避免敏感数据泄露
            log.error("[脱敏] 处理失败，降级为安全响应", e);
            if (result instanceof Result<?> r) {
                r.setData(null);
                r.setCode(500);
                r.setMessage("数据处理异常，请联系管理员");
            }
        }

        return result;
    }

    /**
     * 处理返回结果
     */
    private void processResult(Object result, boolean isAdmin, String currentUserId) {
        if (result instanceof Result<?> r) {
            Object data = r.getData();
            if (data != null) {
                Object desensitized = desensitizeObject(data, isAdmin, currentUserId);
                r.setData(desensitized);
            }
            return;
        }
        desensitizeObject(result, isAdmin, currentUserId);
    }

    /**
     * 递归脱敏对象
     *
     * 修复：
     *   - M6：使用 getFields() 替代 getDeclaredFields() 包含父类字段
     *   - M7：使用 IdentityHashMap 检测循环引用，防止 StackOverflow
     */
    @SuppressWarnings("unchecked")
    private Object desensitizeObject(Object obj, boolean isAdmin, String currentUserId) {
        return desensitizeObject(obj, isAdmin, currentUserId, new java.util.IdentityHashMap<>());
    }

    private Object desensitizeObject(Object obj, boolean isAdmin, String currentUserId,
                                      java.util.IdentityHashMap<Object, Boolean> visited) {
        if (obj == null) {
            return null;
        }
        // 循环引用检测
        if (visited.containsKey(obj)) {
            return obj;
        }
        visited.put(obj, Boolean.TRUE);

        if (obj instanceof Collection<?> coll) {
            List<Object> result = new ArrayList<>(coll.size());
            for (Object item : coll) {
                result.add(desensitizeObject(item, isAdmin, currentUserId, visited));
            }
            return result;
        }
        if (obj.getClass().isArray() && obj.getClass().getComponentType().isPrimitive() == false) {
            Object[] arr = (Object[]) obj;
            for (int i = 0; i < arr.length; i++) {
                arr[i] = desensitizeObject(arr[i], isAdmin, currentUserId, visited);
            }
            return arr;
        }
        if (isPrimitiveLike(obj)) {
            return obj;
        }

        // 反射处理对象的字段（包含父类）
        try {
            // getFields() 返回所有 public 字段（包括父类）
            // 但 @SensitiveData 注解要保留，需要 declared 方式
            // 这里用循环向上遍历的方式
            for (Field field : getAllFields(obj.getClass())) {
                SensitiveData annotation = field.getAnnotation(SensitiveData.class);
                field.setAccessible(true);
                Object value = field.get(obj);

                if (annotation != null) {
                    boolean shouldMask = true;
                    if (!annotation.maskForAdmin() && isAdmin) {
                        shouldMask = false;
                    } else if (isOwnerField(field) && matchOwner(value, currentUserId)) {
                        shouldMask = false;
                    }
                    if (shouldMask && value instanceof String s) {
                        field.set(obj, desensitizationUtil.desensitize(s, annotation.type()));
                    }
                } else if (value != null && !isPrimitiveLike(value)) {
                    desensitizeObject(value, isAdmin, currentUserId, visited);
                }
            }
        } catch (IllegalAccessException e) {
            log.debug("[脱敏] 反射访问失败: {}", e.getMessage());
        }
        return obj;
    }

    /**
     * 递归获取类及所有父类的声明字段
     */
    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                fields.add(field);
            }
            clazz = clazz.getSuperclass();
        }
        return fields;
    }

    /**
     * 判断字段是否是"归属人"字段（用于判断是否本人）
     * 修复（m8）：严格只匹配 userId 字段名，不再匹配 "id"
     * 原因：很多实体都有 id 字段（如 Task.id 是任务 ID），会误判
     */
    private boolean isOwnerField(Field field) {
        return "userId".equals(field.getName());
    }

    /**
     * 判断字段值是否匹配当前用户（本人）
     */
    private boolean matchOwner(Object value, String currentUserId) {
        return value != null && value.toString().equals(currentUserId);
    }

    private boolean isCurrentUserAdmin() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getAuthorities() == null) {
                return false;
            }
            return auth.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        } catch (Exception e) {
            return false;
        }
    }

    private String currentUserId() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) return null;
            Object principal = auth.getPrincipal();
            return principal != null ? principal.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isPrimitiveLike(Object obj) {
        return obj instanceof String
                || obj instanceof Number
                || obj instanceof Boolean
                || obj instanceof Character
                || obj.getClass().isPrimitive()
                || obj.getClass().isEnum()
                || obj instanceof java.time.temporal.Temporal;
    }
}
