import request from '@/utils/request'
import type { Priority, TaskStatus } from '@/types/api'

/**
 * P2.1: 任务列表查询参数
 */
export interface TaskListParams {
  pageNum?: number
  pageSize?: number
  status?: TaskStatus
  priority?: Priority
  keyword?: string
  creatorId?: string
  assigneeId?: string
}

/**
 * P2.1: 任务创建请求体
 */
export interface TaskCreateRequest {
  title: string
  description?: string
  priority: Priority
  assigneeId: string
  estimatedDuration?: number
  sourceRemark?: string
  // TaskCreateView 会附带 creatorId（当前用户），后端会自动忽略
  creatorId?: string
}

/**
 * 获取任务列表
 * P2.1: 返回后端 Result.data（PageResult<TaskVO>）
 */
export function getTaskList(params: TaskListParams) {
  return request({
    url: '/tasks',
    method: 'get',
    params
  })
}

/**
 * 获取任务详情
 * 返回后端 Result.data（TaskVO）
 */
export function getTaskDetail(id: number) {
  return request({
    url: `/tasks/${id}`,
    method: 'get'
  })
}

/**
 * 创建任务
 */
export function createTask(data: TaskCreateRequest) {
  return request({
    url: '/tasks',
    method: 'post',
    data
  })
}

/**
 * 更新任务
 */
export function updateTask(id: number, data: Partial<TaskCreateRequest>) {
  return request({
    url: `/tasks/${id}`,
    method: 'put',
    data
  })
}

/**
 * 删除任务
 */
export function deleteTask(id: number) {
  return request({
    url: `/tasks/${id}`,
    method: 'delete'
  })
}

/**
 * 确认接收任务
 */
export function acceptTask(id: number) {
  return request({
    url: `/tasks/${id}/accept`,
    method: 'post'
  })
}

/**
 * 提交任务
 */
export function submitTask(id: number, data: { remark?: string }) {
  return request({
    url: `/tasks/${id}/submit`,
    method: 'post',
    data
  })
}

/**
 * 验收任务
 */
export function completeTask(id: number) {
  return request({
    url: `/tasks/${id}/complete`,
    method: 'post'
  })
}

/**
 * 驳回任务
 */
export function rejectTask(id: number, reason: string) {
  return request({
    url: `/tasks/${id}/reject`,
    method: 'post',
    data: { reason }
  })
}

/**
 * 取消任务（仅发起方，PENDING_ACCEPT 状态）
 * 修复（C2）：之前未导出，导致前端 TaskDetailView.vue 调用 cancelTask 报错
 */
export function cancelTask(id: number, reason: string) {
  return request({
    url: `/tasks/${id}/cancel`,
    method: 'post',
    data: { reason }
  })
}

/**
 * 撤回任务（仅发起方，PENDING_ACCEPT 状态）
 * 与 cancelTask 同义，但语义更准确（业务上叫"撤回"而非"取消"）
 */
export function withdrawTask(id: number, reason: string) {
  return cancelTask(id, reason)
}
