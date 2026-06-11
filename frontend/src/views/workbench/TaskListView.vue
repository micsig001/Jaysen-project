<template>
  <div class="task-list-container">
    <!-- Filter Bar -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" class="filter-form">
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="待接收" value="PENDING_ACCEPT" />
            <el-option label="进行中" value="IN_PROGRESS" />
            <el-option label="待验收" value="PENDING_VERIFY" />
            <el-option label="已完成" value="COMPLETED" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="filters.priority" placeholder="全部" clearable style="width: 100px">
            <el-option label="最高" :value="1" />
            <el-option label="高" :value="2" />
            <el-option label="中" :value="3" />
            <el-option label="低" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadTasks">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Task List -->
    <el-card shadow="never" class="task-list-card">
      <div v-if="loading" class="loading-wrapper">
        <el-skeleton :rows="5" animated />
      </div>

      <el-empty v-else-if="tasks.length === 0" description="暂无任务" />

      <div v-else class="task-items">
        <div
          v-for="task in tasks"
          :key="task.id"
          class="task-item"
          :class="{ 'overdue': task.isOverdue }"
          @click="viewTaskDetail(task.id)"
        >
          <div class="task-header">
            <el-tag
              :type="getPriorityType(task.priority)"
              size="small"
              class="priority-tag"
            >
              {{ getPriorityLabel(task.priority) }}
            </el-tag>
            <span class="task-title">{{ task.title }}</span>
          </div>

          <div class="task-meta">
            <span v-if="task.sourceRemark" class="meta-item">
              <el-icon><User /></el-icon>
              {{ task.sourceRemark }}
            </span>
            <span class="meta-item" v-if="task.actualDeadline">
              <el-icon><Clock /></el-icon>
              截止: {{ formatTime(task.actualDeadline) }}
            </span>
            <el-tag v-if="task.isOverdue" type="danger" size="small">已超时</el-tag>
            <el-tag :type="getStatusType(task.status)" size="small">
              {{ getStatusLabel(task.status) }}
            </el-tag>
          </div>

          <div class="task-actions" v-if="!isMobile">
            <el-button
              v-if="task.status === 'PENDING_ACCEPT'"
              size="small"
              type="primary"
              @click.stop="handleAccept(task)"
            >
              确认接收
            </el-button>
            <el-button
              v-if="task.status === 'IN_PROGRESS'"
              size="small"
              type="success"
              @click.stop="handleSubmit(task)"
            >
              提交完成
            </el-button>
            <el-button
              v-if="task.status === 'PENDING_VERIFY' && canVerify(task)"
              size="small"
              type="primary"
              @click.stop="handleVerify(task)"
            >
              确认完成
            </el-button>
            <el-button
              v-if="task.status === 'PENDING_VERIFY' && canVerify(task)"
              size="small"
              type="warning"
              @click.stop="handleReject(task)"
            >
              驳回
            </el-button>
          </div>
        </div>
      </div>

      <!-- Pagination -->
      <div class="pagination-wrapper" v-if="total > 0">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadTasks"
          @size-change="loadTasks"
        />
      </div>
    </el-card>

    <!-- Mobile Action Sheet -->
    <el-drawer
      v-model="showActionSheet"
      direction="bottom"
      size="auto"
      :with-header="false"
      v-if="isMobile && selectedTask"
    >
      <div class="action-sheet">
        <h4 class="action-sheet-title">{{ selectedTask.title }}</h4>
        <el-button
          v-for="action in currentActions"
          :key="action.value"
          block
          :type="action.type || 'default'"
          @click="handleActionSelect(action)"
        >
          {{ action.label }}
        </el-button>
        <el-button block @click="showActionSheet = false">取消</el-button>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import dayjs from 'dayjs'
import { useUserStore } from '@/stores/user'
import { PRIORITY_LABELS, PRIORITY_TAG_TYPES, type Priority } from '@/utils/labels'

interface Task {
  id: number
  taskNo: string
  title: string
  priority: number
  status: string
  sourceRemark?: string
  actualStartTime?: string
  actualDeadline?: string
  estimatedDuration?: number
  isOverdue: boolean
  creatorId: string
  assigneeId: string
  createdAt: string
}

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const tasks = ref<Task[]>([])
const total = ref(0)
const showActionSheet = ref(false)
const selectedTask = ref<Task | null>(null)

const filters = reactive({
  status: '',
  priority: undefined as number | undefined
})

const pagination = reactive({
  page: 1,
  pageSize: 20
})

const isMobile = computed(() => window.innerWidth < 768)

const currentActions = computed(() => {
  if (!selectedTask.value) return []

  const actions: any[] = []

  if (selectedTask.value.status === 'PENDING_ACCEPT') {
    actions.push({ label: '确认接收', value: 'accept', type: 'primary' })
  } else if (selectedTask.value.status === 'IN_PROGRESS') {
    actions.push({ label: '提交完成', value: 'submit', type: 'primary' })
  } else if (selectedTask.value.status === 'PENDING_VERIFY' && canVerify(selectedTask.value)) {
    actions.push({ label: '确认完成', value: 'verify', type: 'primary' })
    actions.push({ label: '驳回重做', value: 'reject', type: 'warning' })
  }

  actions.push({ label: '查看详情', value: 'detail', type: 'default' })
  return actions
})

onMounted(() => {
  loadTasks()
})

const loadTasks = async () => {
  loading.value = true
  try {
    const params: any = {
      page: pagination.page,
      pageSize: pagination.pageSize,
      sortBy: 'priority'
    }

    if (filters.status) params.status = filters.status
    if (filters.priority) params.priority = filters.priority

    const response: any = await request({ url: '/tasks/my', method: 'get', params })

    if (response.code === 200) {
      tasks.value = response.data.items
      total.value = response.data.total
    }
  } catch (error) {
    console.error('Load tasks error:', error)
    ElMessage.error('加载任务失败')
  } finally {
    loading.value = false
  }
}

const resetFilters = () => {
  filters.status = ''
  filters.priority = undefined
  pagination.page = 1
  loadTasks()
}

const viewTaskDetail = (taskId: number) => {
  router.push(`/tasks/${taskId}`)
}

const handleAccept = async (task: Task) => {
  try {
    await request({ url: `/tasks/${task.id}/accept`, method: 'post' })
    ElMessage.success('已确认接收')
    loadTasks()
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const handleSubmit = async (task: Task) => {
  try {
    await request({ url: `/tasks/${task.id}/submit`, method: 'post' })
    ElMessage.success('已提交完成，等待验收')
    loadTasks()
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const handleVerify = async (task: Task) => {
  try {
    await request({ url: `/tasks/${task.id}/verify`, method: 'post' })
    ElMessage.success('已确认完成')
    loadTasks()
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const handleReject = async (task: Task) => {
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入驳回原因', '驳回重做', {
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })

    await request({ url: `/tasks/${task.id}/reject`, method: 'post', data: { reason } })
    ElMessage.success('已驳回')
    loadTasks()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

const handleActionSelect = (action: any) => {
  if (!selectedTask.value) return

  switch (action.value) {
    case 'accept':
      handleAccept(selectedTask.value)
      break
    case 'submit':
      handleSubmit(selectedTask.value)
      break
    case 'verify':
      handleVerify(selectedTask.value)
      break
    case 'reject':
      handleReject(selectedTask.value)
      break
    case 'detail':
      viewTaskDetail(selectedTask.value.id)
      break
  }

  showActionSheet.value = false
}

const canVerify = (task: Task) => {
  return task.creatorId === userStore.userInfo?.userId
}

const getPriorityType = (priority: number) => {
  return PRIORITY_TAG_TYPES[priority as Priority] || ''
}

const getPriorityLabel = (priority: number) => {
  return PRIORITY_LABELS[priority as Priority] || '中'
}

const getStatusType = (status: string) => {
  const types: Record<string, string> = {
    PENDING_ACCEPT: 'info',
    IN_PROGRESS: '',
    PENDING_VERIFY: 'warning',
    COMPLETED: 'success',
    WITHDRAWN: 'info'
  }
  return types[status] || ''
}

const getStatusLabel = (status: string) => {
  const labels: Record<string, string> = {
    PENDING_ACCEPT: '待接收',
    IN_PROGRESS: '进行中',
    PENDING_VERIFY: '待验收',
    COMPLETED: '已完成',
    WITHDRAWN: '已撤回'
  }
  return labels[status] || status
}

const formatTime = (time: string) => {
  return dayjs(time).format('MM-DD HH:mm')
}
</script>

<style scoped>
.task-list-container {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.filter-card {
  flex-shrink: 0;
}

.filter-form {
  margin: 0;
}

.task-list-card {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.loading-wrapper {
  padding: 20px;
}

.task-items {
  flex: 1;
  overflow-y: auto;
}

.task-item {
  padding: 16px;
  border-bottom: 1px solid #e4e7ed;
  cursor: pointer;
  transition: background-color 0.2s;
}

.task-item:hover {
  background-color: #f5f7fa;
}

.task-item.overdue {
  background-color: #fef0f0;
  border-left: 3px solid #f56c6c;
}

.task-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.priority-tag {
  flex-shrink: 0;
}

.task-title {
  font-size: 15px;
  font-weight: 500;
  color: #303133;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: #909399;
  flex-wrap: wrap;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.task-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}

.pagination-wrapper {
  padding: 16px 0 0;
  display: flex;
  justify-content: flex-end;
}

.action-sheet {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.action-sheet-title {
  margin: 0 0 8px;
  font-size: 15px;
  color: #303133;
  text-align: center;
  font-weight: 500;
}

/* Mobile optimization */
@media (max-width: 768px) {
  .filter-form {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .filter-form :deep(.el-form-item) {
    margin-right: 0;
    margin-bottom: 0;
  }

  .task-item {
    padding: 12px;
  }

  .task-title {
    font-size: 14px;
  }

  .task-meta {
    font-size: 12px;
  }

  .task-actions {
    display: none; /* Hide buttons on mobile, use action sheet instead */
  }
}
</style>
