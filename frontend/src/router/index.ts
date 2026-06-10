import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/request'
import { useUserStore } from '@/stores/user'

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
  if (token && !userStore.userInfo) {
    try {
      // TODO: 调用获取用户信息API
      // const response = await getUserInfo()
      // userStore.setUserInfo(response.data)
    } catch {
      // Token失效，清除并跳转登录
      localStorage.removeItem('access_token_encrypted')
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

export default router
