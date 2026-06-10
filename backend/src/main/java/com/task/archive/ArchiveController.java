package com.task.archive;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.task.archive.dto.ArchiveResultVO;
import com.task.archive.dto.ArchiveTaskQuery;
import com.task.archive.dto.ArchiveTaskVO;
import com.task.common.BusinessException;
import com.task.common.Result;
import com.task.entity.User;
import com.task.mapper.UserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 归档控制器
 *
 * 接口列表：
 *   - POST /api/archive/trigger    手动触发归档（仅 ADMIN）
 *   - GET  /api/archive/tasks      分页查询历史任务（按角色过滤）
 *   - GET  /api/archive/pending-count  统计待归档数（ADMIN/MANAGER）
 *
 * @author Mavis
 */
@Slf4j
@RestController
@RequestMapping("/api/archive")
@RequiredArgsConstructor
@Tag(name = "归档管理", description = "历史任务查询、归档触发")
public class ArchiveController {

    private final ArchiveService archiveService;
    private final UserMapper userMapper;

    /**
     * 手动触发归档
     * 权限：仅 ADMIN
     */
    @PostMapping("/trigger")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "手动触发归档", description = "仅 ADMIN 可用。归档规则：创建时间 > 1年 且 状态为终态")
    public Result<ArchiveResultVO> triggerArchive() {
        String operatorId = currentUserId();
        log.info("[归档] 手动触发，操作人={}", operatorId);

        ArchiveResultVO result = archiveService.executeArchive("MANUAL", operatorId);
        return Result.success("归档任务已执行", result);
    }

    /**
     * 分页查询历史任务
     * 权限：所有已认证用户，按角色自动过滤可见范围
     */
    @GetMapping("/tasks")
    @Operation(summary = "分页查询历史任务",
               description = "EMPLOYEE 仅自己；MANAGER 本部门；ADMIN 全部。支持关键词、状态、归档时间、原始创建时间筛选")
    public Result<IPage<ArchiveTaskVO>> listArchivedTasks(ArchiveTaskQuery query) {
        User currentUser = currentUser();
        IPage<ArchiveTaskVO> page = archiveService.queryArchivedTasks(query, currentUser);
        return Result.success(page);
    }

    /**
     * 统计待归档任务数
     * 权限：ADMIN/MANAGER（用于预判和管理）
     */
    @GetMapping("/pending-count")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "统计待归档任务数", description = "ADMIN/MANAGER 可见")
    public Result<Long> pendingArchiveCount() {
        long count = archiveService.countPendingArchive();
        return Result.success(count);
    }

    // ============================================
    // 工具方法
    // ============================================

    /**
     * 获取当前登录用户 UserID（来自 JWT principal）
     */
    private String currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null) {
            throw BusinessException.unauthorized("未登录");
        }
        return principal.toString();
    }

    /**
     * 获取当前登录用户的完整信息
     */
    private User currentUser() {
        String userId = currentUserId();
        User user = userMapper.selectByUserId(userId);
        if (user == null) {
            throw BusinessException.unauthorized("用户不存在: " + userId);
        }
        return user;
    }
}
