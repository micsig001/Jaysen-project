import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/request'
import { getUserById } from '@/api/user'
import { useUserStore } from '@/stores/user'
import type { UserInfo } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/login/LoginView.vue')
    },
    {
      path: '/',
      name: 'Layout',
      component: () => import('@/views/workbench/WorkbenchLayout.vue'),
      redirect: '/tasks',
      children: [
        {
          path: 'tasks',
          name: 'TaskList',
          component: () => import('@/views/task/TaskListView.vue')
        },
        {
          path: 'tasks/create',
          name: 'TaskCreate',
          component: () => import('@/views/task/TaskCreateView.vue')
        },
        {
          path: 'tasks/:id',
          name: 'TaskDetail',
          component: () => import('@/views/task/TaskDetailView.vue')
        },
        {
          path: 'visualization',
          name: 'Visualization',
          component: () => import('@/views/visualization/VisualizationView.vue'),
          meta: { pcOnly: true }
        },
        {
          path: 'history',
          name: 'History',
          component: () => import('@/views/history/HistoryView.vue')
        },
        {
          path: 'admin',
          name: 'Admin',
          component: () => import('@/views/admin/AdminView.vue'),
          meta: { requiresAdmin: true }
        }
      ]
    }
  ]
})

// Route guard - 完善的路由守卫
router.beforeEach(async (to, _from, next) => {
  const token = getToken()
  const userStore = useUserStore()

  // 需要认证的路由
  if (to.path !== '/login' && !token) {
    next('/login')
    return
  }

  // 已登录访问登录页，重定向到首页
  if (to.path === '/login' && token) {
    next('/')
    return
  }

  // 如果有token但还没有用户信息，尝试获取
  // P0.4：调用真实 API（GET /api/users/{userId}）补齐 userInfo（含 role）
  // 解决 isAdmin/isManager 永远 false 的问题
  if (token && !userStore.userInfo) {
    try {
      const userId = parseUserIdFromToken(token)
      if (!userId) {
        // JWT 解析失败：清除 token，跳登录
        localStorage.removeItem('access_token_encrypted')
        localStorage.removeItem('refresh_token_encrypted')
        next('/login')
        return
      }
      const res: any = await getUserById(userId)
      const data = res?.data ?? res
      const userInfo: UserInfo = {
        userId: data.userId ?? userId,
        name: data.name ?? '',
        avatar: data.avatarUrl,
        role: (data.role as UserInfo['role']) ?? 'EMPLOYEE',
        departmentId: data.departmentId
      }
      userStore.setUserInfo(userInfo)
    } catch {
      // Token失效，清除并跳转登录
      localStorage.removeItem('access_token_encrypted')
      localStorage.removeItem('refresh_token_encrypted')
      next('/login')
      return
    }
  }

  // 检查管理员权限
  if (to.meta.requiresAdmin && !userStore.isAdmin) {
    next('/403')
    return
  }

  // 检查PC端限制
  if (to.meta.pcOnly && window.innerWidth < 768) {
    next('/mobile-restricted')
    return
  }

  next()
})

/**
 * 从 JWT token 的 payload 中读取 userId claim。
 *
 * 注意：只解码不验签（签名由后端 JwtAuthenticationFilter 验证）。
 * 本方法仅用于前端路由守卫的"提示性"信息展示，权限校验以服务端为准。
 */
function parseUserIdFromToken(token: string): string | null {
  try {
    const parts = token.split('.')
    if (parts.length !== 3) return null
    // base64url → base64
    const payload = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    // 补齐 padding
    const pad = payload.length % 4
    const padded = pad ? payload + '='.repeat(4 - pad) : payload
    const json = atob(padded)
    const claims = JSON.parse(json) as { userId?: string; sub?: string }
    return claims.userId || claims.sub || null
  } catch {
    return null
  }
}

export default router
