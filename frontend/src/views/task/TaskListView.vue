<template>
  <div class="task-list-container">
    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-select v-model="filters.status" placeholder="状态" clearable @change="loadTasks">
        <el-option label="待接收" value="PENDING_ACCEPT" />
        <el-option label="进行中" value="IN_PROGRESS" />
        <el-option label="待验收" value="PENDING_VERIFY" />
        <el-option label="已完成" value="COMPLETED" />
        <el-option label="已驳回" value="REJECTED" />
        <el-option label="已撤回" value="WITHDRAWN" />
      </el-select>

      <el-select v-model="filters.priority" placeholder="优先级" clearable @change="loadTasks">
        <el-option label="最高" :value="1" />
        <el-option label="高" :value="2" />
        <el-option label="中" :value="3" />
        <el-option label="低" :value="4" />
      </el-select>

      <el-button type="primary" @click="handleCreate">新建任务</el-button>
    </div>

    <!-- 任务列表 -->
    <div class="task-list" v-loading="loading">
      <div v-if="taskList.length === 0" class="empty-state">
        <el-empty description="暂无任务" />
      </div>

      <div
        v-for="task in taskList"
        :key="task.id"
        class="task-item"
        :class="{ 'overdue': task.isOverdue }"
        @click="handleViewDetail(task.id)"
      >
        <div class="task-header">
          <el-tag :type="getPriorityType(task.priority)" size="small">
            {{ getPriorityLabel(task.priority) }}
          </el-tag>
          <span class="task-title">{{ task.title }}</span>
          <el-tag v-if="task.isOverdue" type="danger" size="small">已超时</el-tag>
        </div>

        <div class="task-info">
          <span class="info-item">来源：{{ task.sourceRemark || '无' }}</span>
          <span class="info-item">
            截止时间：{{ formatTime(task.actualDeadline) }}
          </span>
          <span class="info-item">状态：{{ getStatusLabel(task.status) }}</span>
        </div>

        <!-- PC 端操作按钮 -->
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
            提交
          </el-button>
          <el-button
            v-if="task.status === 'PENDING_VERIFY' && isInitiator"
            size="small"
            type="success"
            @click.stop="handleComplete(task)"
          >
            验收
          </el-button>
          <el-button
            v-if="task.status === 'PENDING_VERIFY' && isInitiator"
            size="small"
            type="warning"
            @click.stop="handleReject(task)"
          >
            驳回
          </el-button>
        </div>
      </div>
    </div>

    <!-- 移动端操作 Action Sheet -->
    <el-drawer v-model="showActionSheet" direction="bottom" size="auto">
      <div class="action-sheet">
        <el-button
          v-if="selectedTask?.status === 'PENDING_ACCEPT'"
          block
          type="primary"
          @click="handleAccept(selectedTask)"
        >
          确认接收
        </el-button>
        <el-button
          v-if="selectedTask?.status === 'IN_PROGRESS'"
          block
          type="success"
          @click="handleSubmit(selectedTask)"
        >
          提交
        </el-button>
        <el-button
          v-if="selectedTask?.status === 'PENDING_VERIFY'"
          block
          type="success"
          @click="handleComplete(selectedTask)"
        >
          验收
        </el-button>
        <el-button
          v-if="selectedTask?.status === 'PENDING_VERIFY'"
          block
          type="warning"
          @click="handleReject(selectedTask)"
        >
          驳回
        </el-button>
      </div>
    </el-drawer>

    <!-- 分页 -->
    <div class="pagination">
      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        layout="prev, pager, next"
        @current-change="loadTasks"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getTaskList, acceptTask, submitTask, completeTask, rejectTask } from '@/api/task'
import { useUserStore } from '@/stores/user'
import { STATUS_LABELS, type TaskStatus } from '@/utils/labels'
import type { TaskVO, Priority } from '@/types/api'
import dayjs from 'dayjs'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const showActionSheet = ref(false)
// P2.1: 收紧 ref<any> → ref<TaskVO | null>
const selectedTask = ref<TaskVO | null>(null)

// 检测设备类型
const isMobile = ref(window.innerWidth < 768)

// 是否为发起方/执行方（基于当前用户判断）
const isInitiator = computed(() => {
  return selectedTask.value?.creatorId === userStore.userInfo?.userId
})

const filters = reactive<{
  status: TaskStatus | ''
  priority: Priority | undefined
}>({
  status: '',
  priority: undefined
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

// P2.1: 收紧 ref<any> → ref<TaskVO | null>（selectedTask）
//        taskList 暂留 any[]，因为 list 项运行时附加 isOverdue 字段，
//        收紧会让 v-for / v-bind 处需新增 7+ 类型修复，性价比低
const taskList = ref<any[]>([])

// 监听窗口大小变化（P1.6：onUnmounted 清理 resize 监听器，避免内存泄漏）
const handleResize = () => {
  isMobile.value = window.innerWidth < 768
}

onMounted(() => {
  loadTasks()
  window.addEventListener('resize', handleResize)
})
onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})

const loadTasks = async () => {
  loading.value = true
  try {
    const response = await getTaskList({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      status: filters.status || undefined,
      priority: filters.priority || undefined
    })

    const data = response.data
    taskList.value = data.list.map((task: any) => ({
      ...task,
      isOverdue: checkOverdue(task)
    }))
    pagination.total = data.total
  } catch (error) {
    console.error('加载任务列表失败:', error)
    ElMessage.error('加载任务列表失败')
  } finally {
    loading.value = false
  }
}

const checkOverdue = (task: any) => {
  if (!task.actualDeadline || task.status === 'COMPLETED') {
    return false
  }
  return dayjs().isAfter(dayjs(task.actualDeadline))
}

const formatTime = (time: string) => {
  if (!time) return '待定'
  return dayjs(time).format('YYYY-MM-DD HH:mm')
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
  return STATUS_LABELS[status as TaskStatus] || status
}

const handleCreate = () => {
  router.push('/tasks/create')
}

const handleViewDetail = (id: number) => {
  router.push(`/tasks/${id}`)
}

const handleAccept = async (task: any) => {
  try {
    await ElMessageBox.confirm('确认接收此任务吗？', '提示', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'info'
    })

    await acceptTask(task.id)
    ElMessage.success('已确认接收')
    loadTasks()
    showActionSheet.value = false
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '操作失败')
    }
  }
}

const handleSubmit = async (task: any) => {
  try {
    const { value: remark } = await ElMessageBox.prompt('请输入提交备注', '提交任务', {
      confirmButtonText: '提交',
      cancelButtonText: '取消',
      inputPattern: /.+/,
      inputErrorMessage: '备注不能为空'
    })

    await submitTask(task.id, { remark })
    ElMessage.success('已提交待验收')
    loadTasks()
    showActionSheet.value = false
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '操作失败')
    }
  }
}

const handleComplete = async (task: any) => {
  try {
    await ElMessageBox.confirm('确认验收通过吗？', '验收任务', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'success'
    })

    await completeTask(task.id)
    ElMessage.success('任务已验收完成')
    loadTasks()
    showActionSheet.value = false
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '操作失败')
    }
  }
}

const handleReject = async (task: any) => {
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入驳回原因', '驳回任务', {
      confirmButtonText: '驳回',
      cancelButtonText: '取消',
      inputPattern: /.+/,
      inputErrorMessage: '驳回原因不能为空'
    })

    await rejectTask(task.id, reason)
    ElMessage.success('已驳回任务')
    loadTasks()
    showActionSheet.value = false
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '操作失败')
    }
  }
}

// 移动端点击任务显示操作菜单（暂未使用，预留）
// const handleTaskClick = (task: any) => {
//   if (isMobile.value) {
//     selectedTask.value = task
//     showActionSheet.value = true
//   }
// }
</script>

<style scoped>
.task-list-container {
  padding: 16px;
  min-height: 100vh;
  background: #f5f5f5;
}

.filter-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.filter-bar .el-select {
  flex: 1;
  min-width: 120px;
}

.task-list {
  background: white;
  border-radius: 8px;
  overflow: hidden;
}

.empty-state {
  padding: 40px 0;
}

.task-item {
  padding: 16px;
  border-bottom: 1px solid #eee;
  cursor: pointer;
  transition: background 0.2s;
}

.task-item:hover {
  background: #f9f9f9;
}

.task-item.overdue {
  background: #fff2f0;
}

.task-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.task-title {
  flex: 1;
  font-weight: 500;
  font-size: 16px;
  color: #333;
}

.task-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 14px;
  color: #666;
}

.info-item {
  display: block;
}

.task-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}

.action-sheet {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* Mobile optimization */
@media (max-width: 768px) {
  .task-list-container {
    padding: 12px;
  }

  .filter-bar {
    flex-direction: column;
  }

  .filter-bar .el-select {
    width: 100%;
  }

  .task-title {
    font-size: 14px;
  }

  .task-info {
    font-size: 12px;
  }

  .task-actions {
    display: none; /* 移动端隐藏，使用 Action Sheet */
  }
}
</style>
