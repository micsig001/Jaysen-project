/**
 * P2.2: 共享的角色/状态/优先级 常量与标签
 *
 * <p>所有 *.vue 文件应从这里 import，不要在本地重新定义。
 * 字段名 / 数值 与后端 {@code com.task.util.Labels} 完全一致。</p>
 *
 * <p>状态值对应 {@code com.task.entity.Task.status}：</p>
 * <ul>
 *   <li>PENDING_ACCEPT — 待接收</li>
 *   <li>IN_PROGRESS — 进行中</li>
 *   <li>PENDING_VERIFY — 待验收</li>
 *   <li>COMPLETED — 已完成</li>
 *   <li>WITHDRAWN — 已撤回</li>
 *   <li>REJECTED — 已驳回</li>
 * </ul>
 *
 * <p>角色对应 {@code com.task.entity.User.role}：</p>
 * <ul>
 *   <li>EMPLOYEE — 普通员工</li>
 *   <li>MANAGER — 部门经理</li>
 *   <li>ADMIN — 超级管理员</li>
 * </ul>
 *
 * <p>优先级对应 {@code Task.priority} (1=最高 2=高 3=中 4=低)：</p>
 */

export const TASK_STATUS = {
  PENDING_ACCEPT: 'PENDING_ACCEPT',
  IN_PROGRESS: 'IN_PROGRESS',
  PENDING_VERIFY: 'PENDING_VERIFY',
  COMPLETED: 'COMPLETED',
  WITHDRAWN: 'WITHDRAWN',
  REJECTED: 'REJECTED'
} as const

export type TaskStatus = typeof TASK_STATUS[keyof typeof TASK_STATUS]

export const STATUS_LABELS: Record<TaskStatus, string> = {
  PENDING_ACCEPT: '待接收',
  IN_PROGRESS: '进行中',
  PENDING_VERIFY: '待验收',
  COMPLETED: '已完成',
  WITHDRAWN: '已撤回',
  REJECTED: '已驳回'
}

export const ROLE = {
  EMPLOYEE: 'EMPLOYEE',
  MANAGER: 'MANAGER',
  ADMIN: 'ADMIN'
} as const

export type Role = typeof ROLE[keyof typeof ROLE]

export const ROLE_LABELS: Record<Role, string> = {
  EMPLOYEE: '普通员工',
  MANAGER: '部门经理',
  ADMIN: '超级管理员'
}

/**
 * Element Plus el-tag type，用于 ADMIN 红、MANAGER 黄、EMPLOYEE 灰
 */
export const ROLE_TAG_TYPES: Record<Role, '' | 'success' | 'warning' | 'info' | 'danger'> = {
  EMPLOYEE: 'info',
  MANAGER: 'warning',
  ADMIN: 'danger'
}

/** 优先级数值常量（与后端 Task 实体 @Min/@Max 一致） */
export const PRIORITY = {
  HIGHEST: 1,
  HIGH: 2,
  MEDIUM: 3,
  LOW: 4
} as const

export type Priority = typeof PRIORITY[keyof typeof PRIORITY]

export const PRIORITY_LABELS: Record<Priority, string> = {
  [PRIORITY.HIGHEST]: '最高',
  [PRIORITY.HIGH]: '高',
  [PRIORITY.MEDIUM]: '中',
  [PRIORITY.LOW]: '低'
}

export const PRIORITY_TAG_TYPES: Record<Priority, '' | 'success' | 'warning' | 'info' | 'danger'> = {
  [PRIORITY.HIGHEST]: 'danger',
  [PRIORITY.HIGH]: 'warning',
  [PRIORITY.MEDIUM]: '',
  [PRIORITY.LOW]: 'info'
}
