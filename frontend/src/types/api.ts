/**
 * P2.1: 前端共享 API 类型
 *
 * <p>与后端 {@code com.task.*.dto.*VO} 字段保持一致（驼峰命名）。
 * 修改任一处需同步另一处。</p>
 */

import type { Role, TaskStatus, Priority } from '@/utils/labels'

// 重新导出 utils/labels 的类型，方便 import 一处
export type { Role, TaskStatus, Priority }

/**
 * 与后端 {@code com.task.common.Result} 一致
 */
export interface Result<T> {
  code: number
  message: string
  data: T
}

/**
 * 与后端 {@code com.task.common.PageResult} 一致
 */
export interface PageResult<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
}

/**
 * 与后端 {@code com.task.user.dto.UserVO} 一致
 */
export interface UserVO {
  id: number
  userId: string
  name: string
  avatarUrl: string
  role: Role
  departmentId: number | null
  departmentName: string
  position: string
  status: 0 | 1
  manualRole: boolean
  lastSyncTime: string
  createdAt: string
  updatedAt: string
}

/**
 * 与后端 {@code com.task.task.dto.TaskVO} 一致
 */
export interface TaskVO {
  id: number
  taskNo: string
  title: string
  description: string
  priority: Priority
  creatorId: string
  creatorName: string
  assigneeId: string
  assigneeName: string
  estimatedDuration: number
  actualStartTime: string | null
  actualDeadline: string | null
  status: TaskStatus
  sourceRemark: string
  selfAssigned: boolean
  completedAt: string | null
  withdrawnAt: string | null
  createdAt: string
  updatedAt: string
}

/**
 * ECharts 节点类型（VisualizationView 用）
 */
export interface EChartsNode {
  id: string
  name: string
  symbolSize?: number
  category?: number
  value?: number
  label?: {
    show?: boolean
    formatter?: string | ((params: unknown) => string)
  }
  [key: string]: unknown
}

/**
 * ECharts 边类型
 */
export interface EChartsLink {
  source: string
  target: string
  value?: number
  [key: string]: unknown
}
