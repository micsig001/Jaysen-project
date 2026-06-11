package com.task.util;

import java.util.Map;

/**
 * P2.2: 共享的角色 / 任务状态 / 优先级 标签常量
 *
 * <p>前端 {@code frontend/src/utils/labels.ts} 字段与本类完全一致，
 * 修改任一处需同步另一处。</p>
 *
 * @author Mavis
 */
public final class Labels {

    private Labels() {
        // utility class
    }

    // ============================================
    // 角色
    // ============================================

    public static final String ROLE_EMPLOYEE = "EMPLOYEE";
    public static final String ROLE_MANAGER = "MANAGER";
    public static final String ROLE_ADMIN = "ADMIN";

    public static final Map<String, String> ROLE_LABELS = Map.of(
            ROLE_EMPLOYEE, "普通员工",
            ROLE_MANAGER, "部门经理",
            ROLE_ADMIN, "超级管理员"
    );

    // ============================================
    // 任务状态
    // ============================================

    public static final String STATUS_PENDING_ACCEPT = "PENDING_ACCEPT";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_PENDING_VERIFY = "PENDING_VERIFY";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_WITHDRAWN = "WITHDRAWN";
    public static final String STATUS_REJECTED = "REJECTED";

    public static final Map<String, String> STATUS_LABELS = Map.of(
            STATUS_PENDING_ACCEPT, "待接收",
            STATUS_IN_PROGRESS, "进行中",
            STATUS_PENDING_VERIFY, "待验收",
            STATUS_COMPLETED, "已完成",
            STATUS_WITHDRAWN, "已撤回",
            STATUS_REJECTED, "已驳回"
    );

    // ============================================
    // 优先级
    // ============================================

    public static final int PRIORITY_HIGHEST = 1;
    public static final int PRIORITY_HIGH = 2;
    public static final int PRIORITY_MEDIUM = 3;
    public static final int PRIORITY_LOW = 4;

    /**
     * 根据优先级数值返回中文标签
     *
     * @param priority 1=最高 2=高 3=中 4=低
     * @return 中文标签；未知值返回 "中"
     */
    public static String getPriorityLabel(Integer priority) {
        if (priority == null) {
            return "中";
        }
        switch (priority) {
            case PRIORITY_HIGHEST:
                return "最高";
            case PRIORITY_HIGH:
                return "高";
            case PRIORITY_MEDIUM:
                return "中";
            case PRIORITY_LOW:
                return "低";
            default:
                return "中";
        }
    }

    /**
     * 根据角色返回中文标签
     */
    public static String getRoleLabel(String role) {
        if (role == null) {
            return ROLE_LABELS.get(ROLE_EMPLOYEE);
        }
        return ROLE_LABELS.getOrDefault(role, role);
    }

    /**
     * 根据任务状态返回中文标签
     */
    public static String getStatusLabel(String status) {
        if (status == null) {
            return "";
        }
        return STATUS_LABELS.getOrDefault(status, status);
    }
}
