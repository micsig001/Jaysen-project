package com.task.wework;

import com.baomidou.mybatisplus.core.batch.BatchMethod;
import com.baomidou.mybatisplus.core.enums.SqlMethod;
import com.baomidou.mybatisplus.core.toolkit.MybatisBatchUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.task.entity.Department;
import com.task.entity.User;
import com.task.mapper.DepartmentMapper;
import com.task.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 企业微信同步服务
 * 实现基于时间戳的增量同步（Upsert），避免全量覆盖
 *
 * <p>性能优化记录：
 * <ul>
 *   <li>P2-2：用 {@link MybatisBatchUtils} 替代 for-loop 单条 insert/update，
 *       利用 JDBC 预编译 + 批处理，单批次 1 次 round-trip 替代 N 次</li>
 *   <li>P2-3：syncUsers(0L, true) 大数据量 OOM 风险
 *       → 分页处理企微返回的 userlist（每页 1000 条），避免单次加载全公司用户到内存</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeWorkSyncService {

    /** 企微 userlist JSON 解析分页大小（P2-3：避免 OOM） */
    private static final int USER_PARSE_PAGE_SIZE = 1000;

    private final WeWorkApiClient weWorkApiClient;
    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final SqlSessionFactory sqlSessionFactory;

    /**
     * 同步所有部门（批量优化版本）
     */
    @Transactional(rollbackFor = Exception.class)
    public int syncDepartments() {
        log.info("开始同步部门数据");
        int syncCount = 0;

        try {
            // 获取所有部门列表
            JsonNode response = weWorkApiClient.getDepartmentList(0L);
            if (response == null || !response.has("department")) {
                log.error("获取部门列表失败");
                return 0;
            }

            JsonNode departments = response.get("department");
            List<Department> departmentList = new ArrayList<>();

            for (JsonNode deptNode : departments) {
                Department dept = parseDepartment(deptNode);
                if (dept != null) {
                    departmentList.add(dept);
                }
            }

            // 批量查询已存在的部门
            List<String> deptIds = departmentList.stream()
                    .map(Department::getDeptId)
                    .collect(java.util.stream.Collectors.toList());
            
            List<Department> existingDepts = departmentMapper.selectByDeptIds(deptIds);
            java.util.Map<String, Department> existingDeptMap = existingDepts.stream()
                    .collect(java.util.stream.Collectors.toMap(Department::getDeptId, d -> d));

            LocalDateTime now = LocalDateTime.now();
            List<Department> toInsert = new ArrayList<>();
            List<Department> toUpdate = new ArrayList<>();

            // 区分新增和更新
            for (Department dept : departmentList) {
                Department existing = existingDeptMap.get(dept.getDeptId());
                if (existing != null) {
                    // 更新现有部门
                    dept.setId(existing.getId());
                    dept.setCreatedAt(existing.getCreatedAt());
                    dept.setUpdatedAt(now);
                    toUpdate.add(dept);
                } else {
                    // 新增部门
                    dept.setCreatedAt(now);
                    dept.setUpdatedAt(now);
                    toInsert.add(dept);
                }
            }

            // P2-2：批量插入（JDBC batch）
            if (!toInsert.isEmpty()) {
                batchInsert(Department.class, toInsert);
                log.info("批量新增部门: {} 个", toInsert.size());
            }

            // P2-2：批量更新（JDBC batch）
            if (!toUpdate.isEmpty()) {
                batchUpdateById(Department.class, toUpdate);
                log.info("批量更新部门: {} 个", toUpdate.size());
            }

            syncCount = toInsert.size() + toUpdate.size();
            log.info("部门同步完成，共同步 {} 个部门", syncCount);
            return syncCount;
        } catch (Exception e) {
            log.error("同步部门异常", e);
            return syncCount;
        }
    }

    /**
     * 同步指定部门的成员（批量优化版本）
     *
     * @param departmentId 部门 ID
     * @param fetchChild   是否递归获取子部门成员
     */
    @Transactional(rollbackFor = Exception.class)
    public int syncUsers(Long departmentId, boolean fetchChild) {
        log.info("开始同步部门 {} 的成员", departmentId);
        int syncCount = 0;

        try {
            // 获取部门成员列表
            JsonNode response = weWorkApiClient.getDepartmentUsers(departmentId, fetchChild);
            if (response == null || !response.has("userlist")) {
                log.error("获取部门成员失败");
                return 0;
            }

            JsonNode users = response.get("userlist");
            int total = users.size();
            log.info("企微返回 {} 个待同步用户，开始分页处理（每页 {} 条）", total, USER_PARSE_PAGE_SIZE);

            // P2-3：分页处理 userlist，避免一次性加载全公司用户导致 OOM
            for (int offset = 0; offset < total; offset += USER_PARSE_PAGE_SIZE) {
                int end = Math.min(offset + USER_PARSE_PAGE_SIZE, total);
                List<User> userList = new ArrayList<>(end - offset);
                for (int i = offset; i < end; i++) {
                    JsonNode userNode = users.get(i);
                    User user = parseUser(userNode);
                    if (user != null) {
                        userList.add(user);
                    }
                }
                int pageProcessed = persistUserPage(userList);
                syncCount += pageProcessed;
                log.debug("[分页] 已处理 {}/{} 条，本页新增/更新 {} 条",
                        end, total, pageProcessed);
            }

            log.info("部门 {} 成员同步完成，共同步 {} 个用户", departmentId, syncCount);
            return syncCount;
        } catch (Exception e) {
            log.error("同步部门成员异常", e);
            return syncCount;
        }
    }

    /**
     * 单页用户持久化：分类 + 批量 insert/update
     */
    private int persistUserPage(List<User> userList) {
        if (userList.isEmpty()) {
            return 0;
        }

        // 批量查询已存在的用户
        List<String> userIds = userList.stream()
                .map(User::getUserId)
                .collect(java.util.stream.Collectors.toList());

        List<User> existingUsers = userMapper.selectByUserIds(userIds);
        java.util.Map<String, User> existingUserMap = existingUsers.stream()
                .collect(java.util.stream.Collectors.toMap(User::getUserId, u -> u));

        LocalDateTime now = LocalDateTime.now();
        List<User> toInsert = new ArrayList<>();
        List<User> toUpdate = new ArrayList<>();

        // 区分新增和更新
        for (User user : userList) {
            User existing = existingUserMap.get(user.getUserId());
            if (existing != null) {
                // 如果角色是手动设置的，保留原角色
                if (Boolean.TRUE.equals(existing.getManualRole())) {
                    user.setRole(existing.getRole());
                    user.setManualRole(true);
                }
                user.setId(existing.getId());
                user.setCreatedAt(existing.getCreatedAt());
                user.setUpdatedAt(now);
                toUpdate.add(user);
            } else {
                // 新增用户
                user.setCreatedAt(now);
                user.setUpdatedAt(now);
                user.setStatus(1); // 默认启用
                user.setRole("EMPLOYEE"); // 默认角色
                user.setManualRole(false);
                toInsert.add(user);
            }
        }

        // P2-2：批量插入
        if (!toInsert.isEmpty()) {
            batchInsert(User.class, toInsert);
            log.debug("批量新增用户: {} 个", toInsert.size());
        }

        // P2-2：批量更新
        if (!toUpdate.isEmpty()) {
            batchUpdateById(User.class, toUpdate);
            log.debug("批量更新用户: {} 个", toUpdate.size());
        }

        return toInsert.size() + toUpdate.size();
    }

    /**
     * 解析部门 JSON
     */
    private Department parseDepartment(JsonNode node) {
        try {
            Department dept = new Department();
            dept.setDeptId(node.get("id").asText());
            dept.setName(node.get("name").asText());
            dept.setParentId(node.has("parentid") ? node.get("parentid").asLong() : 0L);
            dept.setOrderNum(node.has("order") ? node.get("order").asInt() : 0);
            return dept;
        } catch (Exception e) {
            log.error("解析部门数据失败", e);
            return null;
        }
    }

    /**
     * 解析用户 JSON
     */
    private User parseUser(JsonNode node) {
        try {
            User user = new User();
            user.setUserId(node.get("userid").asText());
            user.setName(node.has("name") ? node.get("name").asText() : user.getUserId());
            user.setMobile(node.has("mobile") ? node.get("mobile").asText() : null);
            user.setEmail(node.has("email") ? node.get("email").asText() : null);
            user.setAvatarUrl(node.has("avatar") ? node.get("avatar").asText() : null);

            // 设置部门 ID（取第一个部门）
            if (node.has("department") && node.get("department").isArray()
                    && node.get("department").size() > 0) {
                user.setDepartmentId(node.get("department").get(0).asLong());
            }

            return user;
        } catch (Exception e) {
            log.error("解析用户数据失败", e);
            return null;
        }
    }

    /**
     * 全量同步（先同步部门，再同步所有成员）
     */
    @Transactional(rollbackFor = Exception.class)
    public void fullSync() {
        log.info("开始全量同步企微数据");

        // 1. 同步部门
        int deptCount = syncDepartments();

        // 2. 同步根部门及所有子部门成员
        int userCount = syncUsers(0L, true);

        log.info("全量同步完成，部门: {}, 用户: {}", deptCount, userCount);
    }

    // ============================================
    // 批量持久化工具（P2-2）
    // ============================================

    /**
     * 批量 INSERT（JDBC batch）
     * 等价于 {@code IService.saveBatch(list)}，通过 MyBatis-Plus 的
     * {@link MybatisBatchUtils} 走 JDBC 预编译 + 批处理，
     * 避免 N 次 round-trip。
     *
     * @param entityClass 实体类（用于查找 BaseMapper）
     * @param list        待插入列表
     * @param <T>         实体类型
     */
    private <T> void batchInsert(Class<T> entityClass, List<T> list) {
        String statementId = getStatementId(entityClass, SqlMethod.INSERT_ONE);
        MybatisBatchUtils.execute(sqlSessionFactory, list, new BatchMethod<>(statementId));
    }

    /**
     * 批量 UPDATE by id（JDBC batch）
     */
    private <T> void batchUpdateById(Class<T> entityClass, List<T> list) {
        String statementId = getStatementId(entityClass, SqlMethod.UPDATE_BY_ID);
        MybatisBatchUtils.execute(sqlSessionFactory, list, new BatchMethod<>(statementId));
    }

    /**
     * 拼接 MyBatis statement id（与 MyBatis-Plus {@code IService} 内部一致）：
     * {@code <mapper-class-canonical-name>.<sqlMethod-key>}
     */
    private String getStatementId(Class<?> entityClass, SqlMethod sqlMethod) {
        // 实体类 -> 对应 BaseMapper（约定：包路径 .entity. → .mapper.，类名 User -> UserMapper）
        String simpleName = entityClass.getSimpleName();
        String mapperSimpleName = simpleName + "Mapper";
        // entity 包: com.task.entity -> mapper 包: com.task.mapper
        String entityPackage = entityClass.getPackageName();
        String mapperPackage = entityPackage.replace(".entity", ".mapper");
        String mapperFqn = mapperPackage + "." + mapperSimpleName;
        return mapperFqn + "." + sqlMethod.getMethod();
    }
}
