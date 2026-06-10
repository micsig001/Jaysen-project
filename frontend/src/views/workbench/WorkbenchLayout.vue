<template>
  <el-container class="layout-container">
    <!-- Sidebar -->
    <el-aside :width="isMobile ? '100%' : '250px'" v-if="!isMobile">
      <div class="sidebar-header">
        <h3>任务协同</h3>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        class="sidebar-menu"
      >
        <el-menu-item index="/tasks">
          <el-icon><List /></el-icon>
          <span>我的任务</span>
        </el-menu-item>
        <el-menu-item index="/visualization">
          <el-icon><Connection /></el-icon>
          <span>关系图谱</span>
          <el-tag size="small" type="info" v-if="isMobile">PC</el-tag>
        </el-menu-item>
        <el-menu-item index="/history">
          <el-icon><Clock /></el-icon>
          <span>历史记录</span>
        </el-menu-item>
        <el-menu-item index="/admin" v-if="userStore.isAdmin">
          <el-icon><Setting /></el-icon>
          <span>管理后台</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- Main Content -->
    <el-container>
      <!-- Header -->
      <el-header class="layout-header">
        <div class="header-left">
          <el-button
            v-if="isMobile"
            icon="Fold"
            circle
            @click="showMobileMenu = !showMobileMenu"
          />
        </div>
        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="32" :src="userStore.userInfo?.avatar" />
              <span class="username">{{ userStore.userInfo?.name }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- Mobile Menu Drawer -->
      <el-drawer
        v-model="showMobileMenu"
        title="菜单"
        direction="ltr"
        size="70%"
        v-if="isMobile"
      >
        <el-menu
          :default-active="activeMenu"
          router
          @select="showMobileMenu = false"
        >
          <el-menu-item index="/tasks">
            <el-icon><List /></el-icon>
            <span>我的任务</span>
          </el-menu-item>
          <el-menu-item index="/history">
            <el-icon><Clock /></el-icon>
            <span>历史记录</span>
          </el-menu-item>
          <el-menu-item index="/admin" v-if="userStore.isAdmin">
            <el-icon><Setting /></el-icon>
            <span>管理后台</span>
          </el-menu-item>
        </el-menu>
      </el-drawer>

      <!-- Content Area -->
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const isMobile = ref(window.innerWidth < 768)
const showMobileMenu = ref(false)

const activeMenu = computed(() => route.path)

// Listen for window resize
const handleResize = () => {
  isMobile.value = window.innerWidth < 768
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})

const handleCommand = (command: string) => {
  if (command === 'logout') {
    ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(async () => {
      // 修复（M1）：使用 request.ts 暴露的 removeToken() 方法
      // 之前直接 removeItem('access_token') 删除的是未加密的 key
      // 但项目用 crypto.ts 加密后存的是 'access_token_encrypted' / 'refresh_token_encrypted'
      // 所以之前的删除根本没生效，token 还在 localStorage 里
      const { removeToken } = await import('@/utils/request')
      removeToken()
      // 兜底：清掉所有可能的 token 相关 key
      localStorage.removeItem('access_token')
      localStorage.removeItem('refresh_token')
      localStorage.removeItem('access_token_encrypted')
      localStorage.removeItem('refresh_token_encrypted')
      // 跳登录页
      router.push('/login')
    })
  }
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.el-aside {
  background-color: #304156;
  color: #fff;
  transition: width 0.3s;
}

.sidebar-header {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.sidebar-header h3 {
  margin: 0;
  font-size: 18px;
  color: #fff;
}

.sidebar-menu {
  border-right: none;
  background-color: transparent;
}

:deep(.el-menu-item) {
  color: rgba(255, 255, 255, 0.7);
}

:deep(.el-menu-item:hover),
:deep(.el-menu-item.is-active) {
  background-color: rgba(255, 255, 255, 0.1) !important;
  color: #fff;
}

.layout-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: #fff;
  border-bottom: 1px solid #e4e7ed;
  padding: 0 20px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.username {
  font-size: 14px;
}

.layout-main {
  background-color: #f0f2f5;
  padding: 20px;
  overflow-y: auto;
}

/* Mobile optimization */
@media (max-width: 768px) {
  .layout-main {
    padding: 10px;
  }

  .username {
    display: none;
  }
}
</style>
