import type { App, Directive, DirectiveBinding } from 'vue'
import { useUserStore } from '@/stores/user'

/**
 * v-permission 权限指令
 *
 * 用法：
 *   <button v-permission="['ADMIN']">删除</button>
 *   <el-button v-permission="['MANAGER', 'ADMIN']">审批</el-button>
 *   <el-button v-permission="'EMPLOYEE'">我的任务</el-button>
 *
 * 行为：
 *   - 当前用户角色匹配 → 显示元素
 *   - 不匹配 → 从 DOM 中移除元素
 *   - 数组形式：匹配任一角色即显示（OR 逻辑）
 *   - 字符串形式：精确匹配
 *
 * @author Mavis
 */
const permission: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
    checkPermission(el, binding)
  },
  updated(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
    checkPermission(el, binding)
  },
}

function checkPermission(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
  const { value } = binding
  if (!value) return

  const userStore = useUserStore()
  const userRole = userStore.userInfo?.role

  if (!userRole) {
    el.parentNode?.removeChild(el)
    return
  }

  const allowed = Array.isArray(value)
    ? value.includes(userRole)
    : value === userRole

  if (!allowed && el.parentNode) {
    el.parentNode.removeChild(el)
  }
}

/**
 * 注册全局指令
 * 在 main.ts 中调用：app.use(setupPermissionDirective)
 */
export function setupPermissionDirective(app: App) {
  app.directive('permission', permission)
}

export default permission
