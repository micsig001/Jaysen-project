<template>
  <div class="task-detail-container" v-loading="loading">
    <el-card v-if="task">
      <template #header>
        <div class="card-header">
          <h2>{{ task.title }}</h2>
          <el-tag :type="getPriorityType(task.priority)">
            {{ getPriorityLabel(task.priority) }}
          </el-tag>
        </div>
      </template>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="状态">
          <el-tag>{{ getStatusLabel(task.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="优先级">
          <el-tag :type="getPriorityType(task.priority)">
            {{ getPriorityLabel(task.priority) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="发起方">
          {{ task.creatorId }}
        </el-descriptions-item>
        <el-descriptions-item label="执行方">
          {{ task.assigneeId }}
        </el-descriptions-item>
        <el-descriptions-item label="来源备注">
          {{ task.sourceRemark || '无' }}
        </el-descriptions-item>
        <el-descriptions-item label="预计耗时">
          {{ task.estimatedHours ? `${task.estimatedHours}小时` : '未设置' }}
        </el-descriptions-item>
        <el-descriptions-item label="实际开始时间">
          {{ formatTime(task.actualStartTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="截止时间">
          {{ formatTime(task.actualDeadline) }}
        </el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="2">
          {{ formatTime(task.createdAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="任务描述" :span="2">
          {{ task.description || '无描述' }}
        </el-descriptions-item>
        <el-descriptions-item v-if="task.rejectReason" label="驳回原因" :span="2">
          {{ task.rejectReason }}
        </el-descriptions-item>
      </el-descriptions>

      <!-- 操作按钮区域 -->
      <div class="action-buttons">
        <el-button
          v-if="task.status === 'PENDING_ACCEPT' && isAssignee"
          type="primary"
          @click="handleAccept"
        >
          确认接收
        </el-button>
        <el-button
          v-if="task.status === 'IN_PROGRESS' && isAssignee"
          type="success"
          @click="handleSubmit"
        >
          提交
        </el-button>
        <el-button
          v-if="task.status === 'PENDING_REVIEW' && isInitiator"
          type="success"
          @click="handleComplete"
        >
          验收通过
        </el-button>
        <el-button
          v-if="task.status === 'PENDING_REVIEW' && isInitiator"
          type="warning"
          @click="handleReject"
        >
          驳回
        </el-button>
        <el-button
          v-if="task.status === 'PENDING_ACCEPT' && isInitiator"
          type="danger"
          @click="handleCancel"
        >
          取消任务
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getTaskDetail, acceptTask, submitTask, completeTask, rejectTask, cancelTask } from '@/api/task'
import { useUserStore } from '@/stores/user'
import dayjs from 'dayjs'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const task = ref<any>(null)

// 基于当前用户判断权限
const isAssignee = computed(() => {
  return task.value?.assigneeId === userStore.userInfo?.userId
})

const isInitiator = computed(() => {
  return task.value?.creatorId === userStore.userInfo?.userId
})

onMounted(() => {
  loadTaskDetail()
})

const loadTaskDetail = async () => {
  loading.value = true
  try {
    const response = await getTaskDetail(Number(route.params.id))
    task.value = response.data
  } catch (error) {
    console.error('加载任务详情失败:', error)
    ElMessage.error('加载任务详情失败')
  } finally {
    loading.value = false
  }
}

const formatTime = (time: string) => {
  if (!time) return '待定'
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

const getPriorityLabel = (priority: number) => {
  const labels: Record<number, string> = {
    1: '最高',
    2: '高',
    3: '中',
    4: '低'
  }
  return labels[priority] || '中'
}

const getPriorityType = (priority: number) => {
  const types: Record<number, any> = {
    1: 'danger',
    2: 'warning',
    3: '',
    4: 'info'
  }
  return types[priority] || ''
}

const getStatusLabel = (status: string) => {
  const labels: Record<string, string> = {
    PENDING_ACCEPT: '待接收',
    IN_PROGRESS: '进行中',
    PENDING_VERIFY: '待验收',
    COMPLETED: '已完成',
    WITHDRAWN: '已撤回',
    REJECTED: '已驳回'
  }
  return labels[status] || status
}

const handleAccept = async () => {
  try {
    await ElMessageBox.confirm('确认接收此任务吗？', '提示', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'info'
    })

    await acceptTask(task.value.id)
    ElMessage.success('已确认接收')
    loadTaskDetail()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '操作失败')
    }
  }
}

const handleSubmit = async () => {
  try {
    const { value: remark } = await ElMessageBox.prompt('请输入提交备注', '提交任务', {
      confirmButtonText: '提交',
      cancelButtonText: '取消',
      inputPattern: /.+/,
      inputErrorMessage: '备注不能为空'
    })

    await submitTask(task.value.id, { remark })
    ElMessage.success('已提交待验收')
    loadTaskDetail()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '操作失败')
    }
  }
}

const handleComplete = async () => {
  try {
    await ElMessageBox.confirm('确认验收通过吗？', '验收任务', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'success'
    })

    await completeTask(task.value.id)
    ElMessage.success('任务已验收完成')
    loadTaskDetail()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '操作失败')
    }
  }
}

const handleReject = async () => {
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入驳回原因', '驳回任务', {
      confirmButtonText: '驳回',
      cancelButtonText: '取消',
      inputPattern: /.+/,
      inputErrorMessage: '驳回原因不能为空'
    })

    await rejectTask(task.value.id, reason)
    ElMessage.success('已驳回任务')
    loadTaskDetail()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '操作失败')
    }
  }
}

const handleCancel = async () => {
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入取消原因', '取消任务', {
      confirmButtonText: '取消',
      cancelButtonText: '返回',
      inputPattern: /.+/,
      inputErrorMessage: '取消原因不能为空'
    })

    await cancelTask(task.value.id, { reason })
    ElMessage.success('任务已取消')
    router.push('/tasks')
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '操作失败')
    }
  }
}
</script>

<style scoped>
.task-detail-container {
  padding: 16px;
  min-height: 100vh;
  background: #f5f5f5;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header h2 {
  margin: 0;
  font-size: 20px;
}

.action-buttons {
  margin-top: 24px;
  display: flex;
  gap: 12px;
  justify-content: center;
}

@media (max-width: 768px) {
  .task-detail-container {
    padding: 12px;
  }

  .card-header h2 {
    font-size: 18px;
  }

  .action-buttons {
    flex-direction: column;
  }

  .action-buttons .el-button {
    width: 100%;
  }
}
</style>
