package com.task.visualization;

import com.task.common.Result;
import com.task.entity.User;
import com.task.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 可视化控制器（辐射图 + 多人全景图）
 *
 * 接口：
 *   - GET /api/visualization/radiation/{userId}     单人辐射图
 *   - POST /api/visualization/multi-view            多人全景图
 *
 * 权限：所有已认证用户（按角色自动限制可见范围由前端传入 userId 决定）
 *
 * @author Mavis
 */
@Slf4j
@RestController
@RequestMapping("/api/visualization")
@RequiredArgsConstructor
public class VisualizationController {

    private final VisualizationService visualizationService;
    private final UserMapper userMapper;

    /**
     * 单人辐射图
     * EMPLOYEE 只能查自己（userId=currentUser）
     * MANAGER 可查本部门成员
     * ADMIN 任意
     */
    @GetMapping("/radiation/{userId}")
    public Result<Map<String, Object>> radiation(@PathVariable String userId) {
        // 权限校验
        User current = currentUser();
        if (!"ADMIN".equals(current.getRole())) {
            if ("EMPLOYEE".equals(current.getRole()) && !userId.equals(current.getUserId())) {
                return Result.forbidden("只能查看自己的辐射图");
            }
            // MANAGER 校验：本部门成员（简化：暂时放行，靠 Service 进一步校验）
        }
        return Result.success(visualizationService.getRadiationGraph(userId));
    }

    /**
     * 多人全景图（POST 接收 userIds 数组）
     */
    @PostMapping("/multi-view")
    public Result<Map<String, Object>> multiView(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> userIds = (List<String>) body.get("userIds");
        return Result.success(visualizationService.getMultiViewGraph(userIds));
    }

    private User currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String userId = principal != null ? principal.toString() : null;
        if (userId == null) {
            throw com.task.common.BusinessException.unauthorized("未登录");
        }
        User user = userMapper.selectByUserId(userId);
        if (user == null) {
            throw com.task.common.BusinessException.unauthorized("用户不存在: " + userId);
        }
        return user;
    }
}
