import request from '@/utils/request'

/**
 * 用户管理 API
 *
 * 后端接口：
 *   GET    /api/users                      分页查询用户列表
 *   GET    /api/users/{userId}             查询用户详情
 *   POST   /api/users/{userId}/disable     禁用账号（ADMIN）
 *   POST   /api/users/{userId}/enable      启用账号（ADMIN）
 *   GET    /api/users/{userId}/role        查询用户角色
 *   PUT    /api/users/{userId}/role        修改用户角色（ADMIN）
 *   GET    /api/users/role-stats           角色人数统计
 */

/**
 * 列表查询参数
 */
export interface ListUsersParams {
  pageNum?: number
  pageSize?: number
  keyword?: string
  role?: 'EMPLOYEE' | 'MANAGER' | 'ADMIN'
  deptId?: number
  status?: 0 | 1
}

/**
 * 分页查询用户列表（ADMIN/MANAGER）
 */
export function listUsers(params: ListUsersParams) {
  return request({
    url: '/users',
    method: 'get',
    params
  })
}

/**
 * 查询用户详情
 */
export function getUserById(userId: string) {
  return request({
    url: `/users/${userId}`,
    method: 'get'
  })
}

/**
 * 禁用用户账号（ADMIN）
 */
export function disableUser(userId: string) {
  return request({
    url: `/users/${userId}/disable`,
    method: 'post'
  })
}

/**
 * 启用用户账号（ADMIN）
 */
export function enableUser(userId: string) {
  return request({
    url: `/users/${userId}/enable`,
    method: 'post'
  })
}

/**
 * 查询用户角色
 */
export function getUserRole(userId: string) {
  return request({
    url: `/users/${userId}/role`,
    method: 'get'
  })
}

/**
 * 修改用户角色（ADMIN）
 */
export function changeUserRole(userId: string, newRole: 'EMPLOYEE' | 'MANAGER' | 'ADMIN') {
  return request({
    url: `/users/${userId}/role`,
    method: 'put',
    data: { newRole }
  })
}

/**
 * 角色人数统计
 */
export function getRoleStats() {
  return request({
    url: '/users/role-stats',
    method: 'get'
  })
}
