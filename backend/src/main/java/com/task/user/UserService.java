package com.task.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.task.common.BusinessException;
import com.task.entity.Department;
import com.task.entity.User;
import com.task.mapper.DepartmentMapper;
import com.task.mapper.UserMapper;
import com.task.user.dto.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户管理 Service
 *
 * <p>职责：用户列表查询、详情查询、启用/禁用账号</p>
 *
 * <p>业务校验：</p>
 * <ul>
 *   <li>不能禁用 ADMIN（保留至少一个超管）</li>
 *   <li>不能操作自己（避免误操作锁死自己）</li>
 *   <li>所有写操作加 @Transactional，失败自动回滚</li>
 * </ul>
 *
 * @author Mavis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;

    /**
     * 分页查询用户列表
     *
     * @param keyword  关键词（匹配 user_id / name / mobile / email，模糊查询）
     * @param role     角色筛选（EMPLOYEE / MANAGER / ADMIN）
     * @param deptId   部门 ID 筛选（users.department_id）
     * @param status   状态筛选（1-启用，0-禁用）
     */
    public Page<UserVO> listUsers(Integer pageNum, Integer pageSize,
                                  String keyword, String role,
                                  Long deptId, Integer status) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            // 多个字段 OR 模糊匹配
            wrapper.and(w -> w.like(User::getUserId, keyword)
                    .or().like(User::getName, keyword)
                    .or().like(User::getMobile, keyword)
                    .or().like(User::getEmail, keyword));
        }
        if (StringUtils.hasText(role)) {
            wrapper.eq(User::getRole, role);
        }
        if (deptId != null) {
            wrapper.eq(User::getDepartmentId, deptId);
        }
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        // 默认按创建时间倒序
        wrapper.orderByDesc(User::getCreatedAt);

        Page<User> page = new Page<>(
                pageNum != null && pageNum > 0 ? pageNum : 1,
                pageSize != null && pageSize > 0 ? pageSize : 10);

        Page<User> result = userMapper.selectPage(page, wrapper);
        return convertPage(result);
    }

    /**
     * 根据 UserID 查询用户详情
     */
    public UserVO getUserById(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw BusinessException.badRequest("用户 ID 不能为空");
        }
        User user = userMapper.selectByUserId(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在: " + userId);
        }
        return toVO(user, lookupDeptName(user.getDepartmentId()));
    }

    /**
     * 禁用用户
     *
     * @param userId     被操作的用户
     * @param operatorId 操作人 UserID（当前登录用户）
     */
    @Transactional(rollbackFor = Exception.class)
    public void disableUser(String userId, String operatorId) {
        if (!StringUtils.hasText(userId)) {
            throw BusinessException.badRequest("用户 ID 不能为空");
        }
        if (Objects.equals(userId, operatorId)) {
            throw BusinessException.badRequest("不能禁用自己的账号");
        }
        User user = userMapper.selectByUserId(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在: " + userId);
        }
        if ("ADMIN".equals(user.getRole())) {
            throw BusinessException.badRequest("不能禁用管理员账号");
        }
        if (Integer.valueOf(0).equals(user.getStatus())) {
            // 已经是禁用状态，幂等返回
            log.info("[用户] 禁用账号（已是禁用状态，幂等）: userId={}", userId);
            return;
        }
        User update = new User();
        update.setId(user.getId());
        update.setStatus(0);
        userMapper.updateById(update);
        log.info("[用户] 禁用账号: userId={}, operator={}", userId, operatorId);
    }

    /**
     * 启用用户
     */
    @Transactional(rollbackFor = Exception.class)
    public void enableUser(String userId, String operatorId) {
        if (!StringUtils.hasText(userId)) {
            throw BusinessException.badRequest("用户 ID 不能为空");
        }
        if (Objects.equals(userId, operatorId)) {
            throw BusinessException.badRequest("不能对自己的账号执行此操作");
        }
        User user = userMapper.selectByUserId(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在: " + userId);
        }
        if (Integer.valueOf(1).equals(user.getStatus())) {
            log.info("[用户] 启用账号（已是启用状态，幂等）: userId={}", userId);
            return;
        }
        User update = new User();
        update.setId(user.getId());
        update.setStatus(1);
        userMapper.updateById(update);
        log.info("[用户] 启用账号: userId={}, operator={}", userId, operatorId);
    }

    // ============================================
    // 转换工具
    // ============================================

    /**
     * 分页对象转换：User → UserVO，批量补齐部门名
     */
    private Page<UserVO> convertPage(Page<User> result) {
        Page<UserVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<User> records = result.getRecords();
        if (records.isEmpty()) {
            voPage.setRecords(Collections.emptyList());
            return voPage;
        }
        // 收集部门 ID 集合，一次性查回
        Set<Long> deptIds = records.stream()
                .map(User::getDepartmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> deptNameMap = batchLookupDeptNames(deptIds);

        List<UserVO> voList = records.stream()
                .map(u -> toVO(u, deptNameMap.get(u.getDepartmentId())))
                .collect(Collectors.toList());
        voPage.setRecords(voList);
        return voPage;
    }

    /**
     * User 实体 → UserVO
     */
    private UserVO toVO(User u, String departmentName) {
        UserVO vo = new UserVO();
        vo.setId(u.getId());
        vo.setUserId(u.getUserId());
        vo.setName(u.getName());
        vo.setAvatarUrl(u.getAvatarUrl());
        vo.setRole(u.getRole());
        vo.setDepartmentId(u.getDepartmentId());
        vo.setDepartmentName(departmentName);
        vo.setPosition(u.getPosition());
        vo.setStatus(u.getStatus());
        vo.setManualRole(u.getManualRole());
        vo.setLastSyncTime(u.getLastSyncTime());
        vo.setCreatedAt(u.getCreatedAt());
        vo.setUpdatedAt(u.getUpdatedAt());
        return vo;
    }

    /**
     * 单个部门名查询
     */
    private String lookupDeptName(Long deptId) {
        if (deptId == null) {
            return null;
        }
        Department dept = departmentMapper.selectById(deptId);
        return dept != null ? dept.getName() : null;
    }

    /**
     * 批量部门名查询（用于列表关联展示，避免 N+1）
     */
    private Map<Long, String> batchLookupDeptNames(Set<Long> deptIds) {
        if (deptIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> map = new HashMap<>(deptIds.size() * 2);
        for (Long deptId : deptIds) {
            Department dept = departmentMapper.selectById(deptId);
            if (dept != null) {
                map.put(deptId, dept.getName());
            }
        }
        return map;
    }
}
