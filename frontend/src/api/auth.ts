import request from '@/utils/request'

/**
 * 通过企微授权码登录
 */
export function loginByCode(code: string) {
  return request({
    url: '/auth/token',
    method: 'post',
    data: { code }
  })
}

/**
 * 刷新 Token
 */
export function refreshToken(refreshToken: string) {
  return request({
    url: '/auth/refresh',
    method: 'post',
    data: { refresh_token: refreshToken }
  })
}

/**
 * 退出登录
 */
export function logout() {
  return request({
    url: '/auth/logout',
    method: 'post'
  })
}
