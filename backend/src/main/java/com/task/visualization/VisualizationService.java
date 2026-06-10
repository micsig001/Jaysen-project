package com.task.visualization;

import com.task.entity.Task;
import com.task.entity.User;
import com.task.mapper.TaskMapper;
import com.task.mapper.UserMapper;
import com.task.user.dto.UserNameVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 可视化服务（辐射图 + 多人全景图）
 *
 * 数据来源：
 *   - 辐射图：以指定用户为中心，找出与该用户有任务往来的所有人员
 *   - 全景图：选定人员之间所有任务流转关系
 *
 * 权限规则（按角色限制可见范围）：
 *   - ADMIN：任意人员
 *   - MANAGER：本部门成员
 *   - EMPLOYEE：仅自己
 *
 * @author Mavis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisualizationService {

    private final TaskMapper taskMapper;
    private final UserMapper userMapper;

    /** 50 人上限（按交互频繁度截取） */
    private static final int MAX_NODES = 50;

    /**
     * 单人辐射图
     * 返回以 userId 为中心、与之有任务关联的所有人
     *
     * 节点数据：
     *   - id: UserID
     *   - name: 用户名
     *   - value: 任务数
     *   - category: 0=中心用户, 1=直接关联人
     *
     * 边数据：
     *   - source: 中心用户
     *   - target: 关联人
     *   - value: 任务数
     */
    public Map<String, Object> getRadiationGraph(String centerUserId) {
        // 1. 查中心用户
        User centerUser = userMapper.selectByUserId(centerUserId);
        if (centerUser == null) {
            throw new com.task.common.BusinessException.notFound("用户不存在: " + centerUserId);
        }

        // 2. 查与该用户相关的所有任务（作为 creator 或 assignee）
        List<Task> relatedTasks = taskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Task>()
                        .eq(Task::getCreatorId, centerUserId)
                        .or().eq(Task::getAssigneeId, centerUserId)
        );

        // 3. 统计交互人员 + 任务数
        Map<String, Long> relatedUserTaskCount = new HashMap<>();
        for (Task task : relatedTasks) {
            String otherUserId = null;
            if (centerUserId.equals(task.getCreatorId())) {
                otherUserId = task.getAssigneeId();
            } else if (centerUserId.equals(task.getAssigneeId())) {
                otherUserId = task.getCreatorId();
            }
            if (otherUserId != null) {
                relatedUserTaskCount.merge(otherUserId, 1L, Long::sum);
            }
        }

        // 4. 50 人上限：按任务数降序截取
        List<Map.Entry<String, Long>> topRelated = relatedUserTaskCount.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(MAX_NODES - 1) // 减去中心用户
                .collect(Collectors.toList());

        // 5. 批量查这些用户的姓名（修复 P1-11：用 selectNamesByUserIds 避免 N+1）
        List<String> userIds = new ArrayList<>();
        userIds.add(centerUserId);
        for (Map.Entry<String, Long> entry : topRelated) {
            userIds.add(entry.getKey());
        }
        Map<String, String> userNameMap = new HashMap<>();
        List<UserNameVO> nameVOs = userMapper.selectNamesByUserIds(userIds);
        for (UserNameVO vo : nameVOs) {
            userNameMap.put(vo.getUserId(), vo.getName());
        }

        // 6. 构造 ECharts 节点
        List<Map<String, Object>> nodes = new ArrayList<>();
        // 中心节点
        Map<String, Object> centerNode = new LinkedHashMap<>();
        centerNode.put("id", centerUserId);
        centerNode.put("name", userNameMap.getOrDefault(centerUserId, centerUserId));
        centerNode.put("symbolSize", 60);
        centerNode.put("category", 0);
        centerNode.put("value", relatedTasks.size());
        nodes.add(centerNode);
        // 关联节点
        for (Map.Entry<String, Long> entry : topRelated) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", entry.getKey());
            node.put("name", userNameMap.getOrDefault(entry.getKey(), entry.getKey()));
            node.put("symbolSize", 20 + entry.getValue() * 2);
            node.put("category", 1);
            node.put("value", entry.getValue());
            nodes.add(node);
        }

        // 7. 构造边
        List<Map<String, Object>> links = new ArrayList<>();
        for (Map.Entry<String, Long> entry : topRelated) {
            Map<String, Object> link = new LinkedHashMap<>();
            link.put("source", centerUserId);
            link.put("target", entry.getKey());
            link.put("value", entry.getValue());
            links.add(link);
        }

        // 8. 构造响应
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("center", centerUser);
        result.put("nodes", nodes);
        result.put("links", links);
        result.put("total", nodes.size());
        result.put("truncated", relatedUserTaskCount.size() > topRelated.size());
        result.put("generatedAt", LocalDateTime.now());
        return result;
    }

    /**
     * 多人全景图
     * 找出 userIds 之间的所有任务流转关系
     */
    public Map<String, Object> getMultiViewGraph(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new com.task.common.BusinessException.badRequest("userIds 不能为空");
        }
        if (userIds.size() > MAX_NODES) {
            throw new com.task.common.BusinessException.badRequest("最多选择 " + MAX_NODES + " 人");
        }

        // 查询这些用户之间所有任务
        List<Task> tasks = taskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Task>()
                        .in(Task::getCreatorId, userIds)
                        .in(Task::getAssigneeId, userIds)
        );

        // 统计边
        Map<String, Long> edgeCount = new HashMap<>();
        for (Task task : tasks) {
            if (userIds.contains(task.getCreatorId()) && userIds.contains(task.getAssigneeId())) {
                String edge = task.getCreatorId() + "->" + task.getAssigneeId();
                edgeCount.merge(edge, 1L, Long::sum);
            }
        }

        // 构造节点（修复 P1-11：用批量查询避免 N+1）
        Map<String, String> userNameMap = new HashMap<>();
        List<UserNameVO> nameVOs = userMapper.selectNamesByUserIds(userIds);
        for (UserNameVO vo : nameVOs) {
            userNameMap.put(vo.getUserId(), vo.getName());
        }
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (String uid : userIds) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", uid);
            node.put("name", userNameMap.getOrDefault(uid, uid));
            node.put("symbolSize", 40);
            nodes.add(node);
        }

        // 构造边
        List<Map<String, Object>> links = new ArrayList<>();
        for (Map.Entry<String, Long> entry : edgeCount.entrySet()) {
            String[] parts = entry.getKey().split("->");
            if (parts.length == 2) {
                Map<String, Object> link = new LinkedHashMap<>();
                link.put("source", parts[0]);
                link.put("target", parts[1]);
                link.put("value", entry.getValue());
                links.add(link);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodes);
        result.put("links", links);
        result.put("total", nodes.size());
        result.put("edgeCount", edgeCount.size());
        result.put("generatedAt", LocalDateTime.now());
        return result;
    }
}
