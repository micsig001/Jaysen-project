package com.task.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.task.common.BusinessException;
import com.task.entity.User;
import com.task.mapper.UserMapper;
import com.task.user.dto.RoleStatsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 角色管理 Service
 *
 * <p>职责：</p>
 * <ul>
 *   <li>修改用户角色（仅 ADMIN）</li>
 *   <li>统计各角色人数</li>
 * </ul>
 *
 * <p>业务校验：</p>
 * <ul>
 *   <li>role 必须是 EMPLOYEE / MANAGER / ADMIN 之一</li>
 *   <li>不能修改自己的角色（避免误操作把自己降级）</li>
 *   <li>手动设置的角色会标记 is_manual_role=true，
 *       后续企微同步不会覆盖（参见 WeWorkSyncService）</li>
 * </ul>
 *
 * @author Mavis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    /** 合法角色枚举（与前端 @Pattern 校验保持一致） */
    private static final Pattern VALID_ROLE =
            Pattern.compile("^(EMPLOYEE|MANAGER|ADMIN)$");

    private final UserMapper userMapper;

    /**
     * 修改用户角色
     *
     * @param userId     被操作的用户
     * @param newRole    新角色（EMPLOYEE / MANAGER / ADMIN）
     * @param operatorId 操作人 UserID
     */
    @Transactional(rollbackFor = Exception.class)
    public void changeUserRole(String userId, String newRole, String operatorId) {
        // 1) 参数校验
        if (!StringUtils.hasText(userId)) {
            throw BusinessException.badRequest("用户 ID 不能为空");
        }
        if (!StringUtils.hasText(newRole) || !VALID_ROLE.matcher(newRole).matches()) {
            throw BusinessException.badRequest("角色必须是 EMPLOYEE / MANAGER / ADMIN 之一");
        }
        if (Objects.equals(userId, operatorId)) {
            throw BusinessException.badRequest("不能修改自己的角色");
        }

        // 2) 查用户
        User user = userMapper.selectByUserId(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在: " + userId);
        }
        // 3) 幂等：目标角色与当前一致
        if (Objects.equals(newRole, user.getRole())) {
            log.info("[角色] 修改角色（已是目标角色，幂等）: userId={}, role={}", userId, newRole);
            return;
        }

        // 4) 业务约束：保证至少一个 ADMIN 存在
        // 当把 ADMIN 降级时，检查是否还有其他 ADMIN
        if ("ADMIN".equals(user.getRole()) && !"ADMIN".equals(newRole)) {
            long adminCount = countByRole("ADMIN");
            if (adminCount <= 1) {
                throw BusinessException.badRequest("系统必须保留至少 1 个管理员，无法降级");
            }
        }

        // 5) 持久化：标记 is_manual_role=true，避免被企微同步覆盖
        User update = new User();
        update.setId(user.getId());
        update.setRole(newRole);
        update.setManualRole(true);
        userMapper.updateById(update);

        log.info("[角色] 修改角色: userId={}, oldRole={}, newRole={}, operator={}, manualRole=true",
                userId, user.getRole(), newRole, operatorId);
    }

    /**
     * 查询某用户的角色
     */
    public String getUserRole(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw BusinessException.badRequest("用户 ID 不能为空");
        }
        User user = userMapper.selectByUserId(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在: " + userId);
        }
        return user.getRole();
    }

    /**
     * 统计各角色人数
     *
     * <p>返回三种角色的数量（即使为 0 也返回），便于前端展示完整分布</p>
     */
    public List<RoleStatsVO> getRoleStats() {
        // 修复（P1-12）：用单次 GROUP BY 查询代替 3 次 count
        Map<String, Long> dbCount = userMapper.countByRoleGroup().stream()
                .collect(Collectors.toMap(RoleStatsVO::getRole, RoleStatsVO::getCount));
        return Arrays.asList(
                new RoleStatsVO("EMPLOYEE", dbCount.getOrDefault("EMPLOYEE", 0L)),
                new RoleStatsVO("MANAGER", dbCount.getOrDefault("MANAGER", 0L)),
                new RoleStatsVO("ADMIN", dbCount.getOrDefault("ADMIN", 0L))
        );
    }

    /**
     * 按角色统计人数（仅 status=1 的启用账号）
     */
    private long countByRole(String role) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getRole, role).eq(User::getStatus, 1);
        return userMapper.selectCount(wrapper);
    }
}
