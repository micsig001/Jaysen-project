import axios from 'axios'
import type { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import router from '@/router'
import { encrypt, decrypt } from './crypto'
import type { Result } from '@/types/api'

// Token存储的Key
const ACCESS_TOKEN_KEY = 'access_token_encrypted'
const REFRESH_TOKEN_KEY = 'refresh_token_encrypted'

/**
 * 安全地获取Token
 */
function getToken(): string | null {
  const encrypted = localStorage.getItem(ACCESS_TOKEN_KEY)
  if (!encrypted) return null
  return decrypt(encrypted)
}

/**
 * 安全地存储Token
 */
function setToken(token: string): void {
  const encrypted = encrypt(token)
  localStorage.setItem(ACCESS_TOKEN_KEY, encrypted)
}

/**
 * 清除Token
 */
function removeToken(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}

// 创建 axios 实例
const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
    // P1.7：CSRF 防护 — 标识此请求来自前端 XHR。
    // 后端 SecurityConfig 会校验所有 /api/** 路由（公开端点除外）必须带此 header，
    // 缺 header 返回 403，可阻止浏览器 form/anchor 触发的跨站请求（无自定义 header）。
    'X-Requested-With': 'XMLHttpRequest'
  }
})

// 请求拦截器
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  // P2.1: 收紧 res: any → AxiosResponse<Result<T>>，统一响应格式
  (response: AxiosResponse) => {
    const res = response.data as Result<unknown>

    // 如果返回的状态码不是 200，则认为是错误
    if (res.code !== 200) {
      // 401: 未授权，跳转到登录页
      if (res.code === 401) {
        removeToken()
        router.push('/login')
      }

      return Promise.reject(new Error(res.message || '请求失败'))
    }

    return res as unknown as AxiosResponse
  },
  (error) => {
    // 处理 HTTP 状态码
    if (error.response) {
      switch (error.response.status) {
        case 401:
          removeToken()
          router.push('/login')
          break
        case 403:
          break
        case 404:
          break
        case 500:
          break
        default:
          break
      }
    }

    return Promise.reject(error)
  }
)

export { getToken, setToken, removeToken }
export default service
