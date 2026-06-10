package com.task.sync;

import com.task.audit.AuditLog;
import com.task.common.Result;
import com.task.permission.PermissionService;
import com.task.mapper.UserMapper;
import com.task.wework.WeWorkSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 企微同步管理 Controller
 *
 * <p>提供手动触发同步的 HTTP 接口，弥补 {@link SyncScheduler} 只有定时器的不足。
 *
 * <p>权限：仅 ADMIN
 *
 * @author Mavis
 */
@Slf4j
@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
@Tag(name = "企微同步", description = "手动触发 + 状态查询")
public class SyncController {

    private final WeWorkSyncService weWorkSyncService;
    private final PermissionService permissionService;
    private final UserMapper userMapper;

    @Value("${wework.sync.timeout-seconds:300}")
    private int syncTimeoutSeconds;

    /**
     * 手动触发全量同步
     */
    @PostMapping("/trigger")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operationType = "TRIGGER_SYNC", resourceType = "SYSTEM",
            description = "手动触发企微全量同步")
    @Operation(summary = "手动触发全量同步", description = "仅 ADMIN 可用，立即同步部门+用户")
    public Result<Map<String, Object>> triggerSync() {
        log.info("[同步] 手动触发全量同步, operator={}", currentUserId());
        long start = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();

        try {
            // 异步执行（避免 HTTP 超时）
            new Thread(() -> {
                try {
                    weWorkSyncService.fullSync();
                } catch (Exception e) {
                    log.error("[同步] 全量同步失败", e);
                }
            }, "manual-fullsync").start();

            result.put("status", "STARTED");
            result.put("triggerTime", LocalDateTime.now());
            result.put("estimatedDuration", syncTimeoutSeconds + "s");
            result.put("operator", currentUserId());
            return Result.success(result);
        } catch (Exception e) {
            log.error("[同步] 触发同步失败", e);
            result.put("status", "FAILED");
            result.put("error", e.getMessage());
            return Result.error(500, "触发同步失败: " + e.getMessage());
        }
    }

    /**
     * 查询同步状态
     */
    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "查询同步状态", description = "返回上次同步时间和状态")
    public Result<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        // TODO: 接入 SyncLog 表查上次同步时间
        status.put("lastSyncTime", "未实现（待接入 SyncLog）");
        status.put("schedulerEnabled", true);
        status.put("scheduleCron", "0 0 2 * * ? (全量) / 0 0 * * * ? (增量)");
        return Result.success(status);
    }

    /**
     * 查询同步日志
     */
    @GetMapping("/logs")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "查询同步日志", description = "返回最近 N 条同步日志")
    public Result<Object> getLogs(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int limit
    ) {
        // TODO: 接入 SyncLog 表
        return Result.success(java.util.Collections.emptyList());
    }

    private String currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal != null ? principal.toString() : "anonymous";
    }
}
