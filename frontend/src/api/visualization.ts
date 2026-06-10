import request from '@/utils/request'

/**
 * 可视化模块 API
 *
 * 后端接口：
 *   GET  /api/visualization/radiation/{userId}  单人辐射图
 *   POST /api/visualization/multi-view         多人全景图
 */

/**
 * 单人辐射图
 * @param userId 中心用户 ID
 */
export function getRadiationGraph(userId: string) {
  return request({
    url: `/visualization/radiation/${userId}`,
    method: 'get',
  })
}

/**
 * 多人全景图
 * @param body { userIds: string[] }
 */
export function getMultiViewGraph(body: { userIds: string[] }) {
  return request({
    url: '/visualization/multi-view',
    method: 'post',
    data: body,
  })
}
