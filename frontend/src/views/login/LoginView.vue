<template>
  <div class="login-container">
    <el-card class="login-card">
      <template #header>
        <h2 class="login-title">企业级任务协同管理系统</h2>
      </template>

      <el-form :model="loginForm" :rules="rules" ref="formRef">
        <el-form-item prop="code">
          <el-input
            v-model="loginForm.code"
            placeholder="请输入授权码（企微自动获取）"
            prefix-icon="Key"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            class="login-btn"
            :loading="loading"
            @click="handleLogin"
          >
            {{ loading ? '登录中...' : '登录' }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="tips">
        <p>提示：在企业微信中打开将自动登录</p>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { loginByCode } from '@/api/auth'
import { setToken } from '@/utils/request'

const router = useRouter()
const route = useRoute()
const formRef = ref()
const loading = ref(false)

const loginForm = reactive({
  code: ''
})

const rules = {
  code: [{ required: true, message: '请输入授权码', trigger: 'blur' }]
}

// Get code from URL query params (WeWork OAuth callback)
onMounted(() => {
  const code = route.query.code as string
  if (code) {
    loginForm.code = code
    handleLogin()
  }
})

const handleLogin = async () => {
  if (!loginForm.code) {
    ElMessage.warning('请先获取授权码')
    return
  }

  loading.value = true
  try {
    const response = await loginByCode(loginForm.code)
    
    const { access_token, token_type } = response.data
    // 使用加密方式存储Token
    setToken(access_token)

    ElMessage.success('登录成功')
    router.push('/')
  } catch (error: any) {
    ElMessage.error(error.message || '网络错误，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-card {
  width: 400px;
  max-width: 90vw;
}

.login-title {
  text-align: center;
  margin: 0;
  font-size: 20px;
  color: #333;
}

.login-btn {
  width: 100%;
}

.tips {
  margin-top: 16px;
  text-align: center;
  color: #999;
  font-size: 12px;
}

/* Mobile optimization */
@media (max-width: 768px) {
  .login-card {
    width: 90vw;
  }

  .login-title {
    font-size: 18px;
  }
}
</style>
