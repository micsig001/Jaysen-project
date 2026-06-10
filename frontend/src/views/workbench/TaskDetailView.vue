<template>
  <div class="task-detail-container">
    <el-card v-loading="loading">
      <template #header>
        <div class="card-header">
          <h3>任务详情</h3>
          <el-button @click="$router.back()">返回</el-button>
        </div>
      </template>

      <el-descriptions :column="isMobile ? 1 : 2" border v-if="task">
        <el-descriptions-item label="任务编号">{{ task.taskNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(task.status)">
            {{ getStatusLabel(task.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="标题" :span="2">{{ task.title }}</el-descriptions-item>
        <el-descriptions-item label="优先级">
          <el-tag :type="getPriorityType(task.priority)">
            {{ getPriorityLabel(task.priority) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="来源备注">{{ task.sourceRemark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">
          {{ task.actualStartTime ? formatTime(task.actualStartTime) : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="截止时间">
          {{ task.actualDeadline ? formatTime(task.actualDeadline) : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="预估时长">
          {{ task.estimatedDuration ? `${task.estimatedDuration}分钟` : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="是否超时" v-if="task.isOverdue">
          <el-tag type="danger">已超时</el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <div class="action-buttons" v-if="task">
        <el-button
          v-if="task.status === 'PENDING_ACCEPT'"
          type="primary"
          @click="handleAccept"
        >
          确认接收
        </el-button>
        <el-button
          v-if="task.status === 'IN_PROGRESS'"
          type="success"
          @click="handleSubmit"
        >
          提交完成
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import dayjs from 'dayjs'

const route = useRoute()

const loading = ref(false)
const task = ref<any>(null)
const isMobile = computed(() => window.innerWidth < 768)

onMounted(() => {
  loadTaskDetail()
})

const loadTaskDetail = async () => {
  loading.value = true
  try {
    const response = await axios.get(`/api/tasks/${route.params.id}`)
    if (response.data.code === 200) {
      task.value = response.data.data
    }
  } catch (error) {
    ElMessage.error('加载任务详情失败')
  } finally {
    loading.value = false
  }
}

const handleAccept = async () => {
  try {
    await axios.post(`/api/tasks/${task.value.id}/accept`)
    ElMessage.success('已确认接收')
    loadTaskDetail()
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const handleSubmit = async () => {
  try {
    await axios.post(`/api/tasks/${task.value.id}/submit`)
    ElMessage.success('已提交完成')
    loadTaskDetail()
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const getPriorityType = (priority: number) => {
  const types: Record<number, string> = { 1: 'danger', 2: 'warning', 3: '', 4: 'info' }
  return types[priority] || ''
}

const getPriorityLabel = (priority: number) => {
  const labels: Record<number, string> = { 1: '最高', 2: '高', 3: '中', 4: '低' }
  return labels[priority] || '中'
}

const getStatusType = (status: string) => {
  const types: Record<string, string> = {
    PENDING_ACCEPT: 'info', IN_PROGRESS: '', PENDING_VERIFY: 'warning', COMPLETED: 'success'
  }
  return types[status] || ''
}

const getStatusLabel = (status: string) => {
  const labels: Record<string, string> = {
    PENDING_ACCEPT: '待接收', IN_PROGRESS: '进行中', PENDING_VERIFY: '待验收', COMPLETED: '已完成'
  }
  return labels[status] || status
}

const formatTime = (time: string) => dayjs(time).format('YYYY-MM-DD HH:mm:ss')
</script>

<style scoped>
.task-detail-container {
  max-width: 1200px;
  margin: 0 auto;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header h3 {
  margin: 0;
}

.action-buttons {
  margin-top: 20px;
  display: flex;
  gap: 12px;
  justify-content: center;
}
</style>
