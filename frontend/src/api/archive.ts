import request from '@/utils/request'

/**
 * 归档模块 API
 *
 * 后端接口：
 *   POST /api/archive/trigger       手动触发归档
 *   GET  /api/archive/tasks         分页查询历史任务
 *   GET  /api/archive/pending-count 统计待归档数
 */

/**
 * 手动触发归档（仅 ADMIN）
 */
export function triggerArchive() {
  return request({
    url: '/archive/trigger',
    method: 'post'
  })
}

/**
 * 分页查询历史任务
 * @param params 筛选条件
 */
export function getArchivedTasks(params: {
  keyword?: string
  status?: string
  creatorId?: string
  assigneeId?: string
  archivedAtStart?: string
  archivedAtEnd?: string
  originalCreatedAtStart?: string
  originalCreatedAtEnd?: string
  priority?: number
  pageNum?: number
  pageSize?: number
}) {
  return request({
    url: '/archive/tasks',
    method: 'get',
    params
  })
}

/**
 * 统计待归档任务数（ADMIN/MANAGER）
 */
export function getPendingArchiveCount() {
  return request({
    url: '/archive/pending-count',
    method: 'get'
  })
}
