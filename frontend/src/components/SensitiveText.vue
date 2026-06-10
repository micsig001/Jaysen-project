<template>
  <span :class="['sensitive-text', textClass]">
    <template v-if="!showPlaintext">
      {{ maskedValue }}
      <el-link
        v-if="canView && !readonly"
        type="primary"
        :underline="false"
        class="reveal-btn"
        @click="handleReveal"
      >
        {{ revealed ? '隐藏' : '查看' }}
      </el-link>
    </template>
    <template v-else>
      {{ value }}
    </template>
  </span>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'

/**
 * 敏感数据脱敏展示组件
 *
 * 后端 AOP 已经做了脱敏，前端组件做"查看明文"二次确认 + 自动隐藏
 *
 * 规则：
 *   - 默认显示脱敏值（如"张*"）
 *   - 点击"查看"需要输入二次确认
 *   - ADMIN 角色默认可见明文（后端不脱敏）
 *   - 60 秒后自动隐藏
 *
 * 使用：
 *   <SensitiveText :value="user.idCard" type="ID_CARD" masked-value="110****1234" />
 */
interface Props {
  /** 原始值（仅 ADMIN 可见，其他人需要 reveal） */
  value: string | null | undefined
  /** 敏感类型 */
  type?: string
  /** 脱敏后显示的值（后端 AOP 输出的值） */
  maskedValue?: string
  /** 是否只读（不允许点击"查看"） */
  readonly?: boolean
  /** 是否强制脱敏（即使是 ADMIN） */
  forceMask?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  type: 'NAME',
  maskedValue: '',
  readonly: false,
  forceMask: false,
})

const userStore = useUserStore()

const revealed = ref(false)
let autoHideTimer: number | null = null

const isAdmin = computed(() => userStore.isAdmin)

const canView = computed(() => {
  if (props.forceMask) return false
  return true
})

const showPlaintext = computed(() => {
  if (props.forceMask) return false
  if (isAdmin.value) return true
  return revealed.value
})

const textClass = computed(() => {
  return showPlaintext.value ? 'sensitive-text--plain' : 'sensitive-text--masked'
})

const handleReveal = async () => {
  if (revealed.value) {
    revealed.value = false
    if (autoHideTimer) {
      clearTimeout(autoHideTimer)
      autoHideTimer = null
    }
    return
  }

  // 二次确认
  try {
    const { value } = await ElMessageBox.prompt(
      '查看敏感数据需要二次确认，请输入"查看"以继续',
      '敏感数据查看',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        inputPlaceholder: '请输入"查看"',
        inputValidator: (val: string) => {
          if (val !== '查看') {
            return '请准确输入"查看"二字'
          }
          return true
        },
      }
    )
    if (value !== '查看') return
  } catch {
    return
  }

  revealed.value = true
  ElMessage.success('已显示明文，60 秒后自动隐藏')

  if (autoHideTimer) {
    clearTimeout(autoHideTimer)
  }
  autoHideTimer = window.setTimeout(() => {
    revealed.value = false
    autoHideTimer = null
  }, 60_000)
}

onUnmounted(() => {
  if (autoHideTimer) {
    clearTimeout(autoHideTimer)
  }
})
</script>

<style scoped>
.sensitive-text {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.sensitive-text--masked {
  color: #909399;
}

.sensitive-text--plain {
  color: #303133;
}

.reveal-btn {
  font-size: 12px;
  margin-left: 4px;
}
</style>
