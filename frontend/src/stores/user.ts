import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export interface UserInfo {
  userId: string
  name: string
  avatar?: string
  role: 'EMPLOYEE' | 'MANAGER' | 'ADMIN'
  departmentId?: number
}

export const useUserStore = defineStore('user', () => {
  const userInfo = ref<UserInfo | null>(null)

  const isAdmin = computed(() => userInfo.value?.role === 'ADMIN')
  const isManager = computed(() =>
    userInfo.value?.role === 'MANAGER' || userInfo.value?.role === 'ADMIN'
  )

  function setUserInfo(info: UserInfo) {
    userInfo.value = info
  }

  function clearUserInfo() {
    userInfo.value = null
  }

  return {
    userInfo,
    isAdmin,
    isManager,
    setUserInfo,
    clearUserInfo
  }
})
