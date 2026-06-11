<template>
  <div class="history-container">
    <el-card>
      <template #header>
        <div class="header">
          <h3>历史任务</h3>
          <div class="header-actions">
            <el-tag v-if="isAdmin && pendingCount !== null" type="info" effect="plain">
              待归档: {{ pendingCount }} 条
            </el-tag>
            <el-button
              v-if="isAdmin"
              type="primary"
              :loading="triggering"
              @click="handleTrigger"
            >
              手动归档
            </el-button>
          </div>
        </div>
      </template>

      <!-- 筛选区 -->
      <el-form :inline="true" :model="query" class="filter-form">
        <el-form-item label="关键词">
          <el-input
            v-model="query.keyword"
            placeholder="搜索任务标题"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="query.status"
            placeholder="全部"
            clearable
            style="width: 130px"
          >
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已撤回" value="WITHDRAWN" />
          </el-select>
        </el-form-item>
        <el-form-item label="归档时间">
          <el-date-picker
            v-model="archivedAtRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始"
            end-placeholder="结束"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 360px"
          />
        </el-form-item>
        <el-form-item label="创建时间">
          <el-date-picker
            v-model="originalCreatedAtRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始"
            end-placeholder="结束"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 360px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 列表 -->
      <div class="table-scroll-wrapper">
        <el-table
          v-loading="loading"
          :data="taskList"
          stripe
          border
          style="width: 100%"
        >
          <template #empty>
            <el-empty
              v-if="!loading"
              description="暂无历史任务，试试调整筛选条件"
              :image-size="80"
            />
          </template>
        <el-table-column prop="taskNo" label="任务编号" width="160" />
        <el-table-column prop="title" label="任务标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="优先级" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="priorityType(row.priority)" size="small">
              {{ priorityLabel(row.priority) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="creatorName" label="创建人" width="110" />
        <el-table-column prop="assigneeName" label="执行人" width="110" />
        <el-table-column label="最终状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="originalCreatedAt" label="原始创建时间" width="160" />
        <el-table-column prop="archivedAt" label="归档时间" width="160" />
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      </div>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadList"
          @current-change="loadList"
        />
      </div>
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="历史任务详情" width="720px">
      <el-descriptions v-if="currentTask" :column="2" border>
        <el-descriptions-item label="任务编号" :span="2">
          {{ currentTask.taskNo }}
        </el-descriptions-item>
        <el-descriptions-item label="任务标题" :span="2">
          {{ currentTask.title }}
        </el-descriptions-item>
        <el-descriptions-item label="任务描述" :span="2">
          {{ currentTask.description || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="优先级">
          <el-tag :type="priorityType(currentTask.priority)" size="small">
            {{ priorityLabel(currentTask.priority) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="最终状态">
          <el-tag :type="statusType(currentTask.status)" size="small">
            {{ statusLabel(currentTask.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建人">
          {{ currentTask.creatorName }} ({{ currentTask.creatorId }})
        </el-descriptions-item>
        <el-descriptions-item label="执行人">
          {{ currentTask.assigneeName }} ({{ currentTask.assigneeId }})
        </el-descriptions-item>
        <el-descriptions-item label="预估时长">
          {{ currentTask.estimatedDuration ? currentTask.estimatedDuration + ' 分钟' : '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="来源备注">
          {{ currentTask.sourceRemark || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="实际开始时间">
          {{ currentTask.actualStartTime || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="实际截止时间">
          {{ currentTask.actualDeadline || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="完成时间" v-if="currentTask.completedAt">
          {{ currentTask.completedAt }}
        </el-descriptions-item>
        <el-descriptions-item label="撤回时间" v-if="currentTask.withdrawnAt">
          {{ currentTask.withdrawnAt }}
        </el-descriptions-item>
        <el-descriptions-item label="原始创建时间">
          {{ currentTask.originalCreatedAt }}
        </el-descriptions-item>
        <el-descriptions-item label="原始更新时间">
          {{ currentTask.originalUpdatedAt }}
        </el-descriptions-item>
        <el-descriptions-item label="归档时间" :span="2">
          {{ currentTask.archivedAt }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getArchivedTasks,
  triggerArchive,
  getPendingArchiveCount
} from '@/api/archive'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const isAdmin = computed(() => userStore.isAdmin)

// 列表状态
const loading = ref(false)
const triggering = ref(false)
const taskList = ref<any[]>([])
const total = ref(0)
const pendingCount = ref<number | null>(null)

// 查询条件
const query = reactive({
  keyword: '',
  status: '',
  creatorId: '',
  assigneeId: '',
  archivedAtStart: '',
  archivedAtEnd: '',
  originalCreatedAtStart: '',
  originalCreatedAtEnd: '',
  priority: undefined as number | undefined,
  pageNum: 1,
  pageSize: 10
})

// 时间范围组件的双向绑定
const archivedAtRange = ref<[string, string] | null>(null)
const originalCreatedAtRange = ref<[string, string] | null>(null)

watch(archivedAtRange, (val) => {
  if (val && val.length === 2) {
    query.archivedAtStart = val[0]
    query.archivedAtEnd = val[1]
  } else {
    query.archivedAtStart = ''
    query.archivedAtEnd = ''
  }
})

watch(originalCreatedAtRange, (val) => {
  if (val && val.length === 2) {
    query.originalCreatedAtStart = val[0]
    query.originalCreatedAtEnd = val[1]
  } else {
    query.originalCreatedAtStart = ''
    query.originalCreatedAtEnd = ''
  }
})

// 详情弹窗
const detailVisible = ref(false)
const currentTask = ref<any>(null)

const handleView = (row: any) => {
  currentTask.value = row
  detailVisible.value = true
}

// 加载列表
const loadList = async () => {
  loading.value = true
  try {
    const res: any = await getArchivedTasks(query)
    taskList.value = res.records || []
    total.value = res.total || 0
  } catch (e: any) {
    ElMessage.error('加载历史任务失败: ' + (e?.message || ''))
  } finally {
    loading.value = false
  }
}

// 加载待归档数
const loadPendingCount = async () => {
  if (!isAdmin.value && !userStore.isManager) return
  try {
    const res: any = await getPendingArchiveCount()
    pendingCount.value = typeof res === 'number' ? res : (res?.data ?? null)
  } catch {
    // 静默失败，不影响主流程
  }
}

const handleSearch = () => {
  query.pageNum = 1
  loadList()
}

const handleReset = () => {
  query.keyword = ''
  query.status = ''
  query.creatorId = ''
  query.assigneeId = ''
  query.archivedAtStart = ''
  query.archivedAtEnd = ''
  query.originalCreatedAtStart = ''
  query.originalCreatedAtEnd = ''
  query.priority = undefined
  query.pageNum = 1
  archivedAtRange.value = null
  originalCreatedAtRange.value = null
  loadList()
}

// 手动归档
const handleTrigger = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要手动触发归档吗？将迁移"创建超过1年且状态为终态"的任务。',
      '手动归档',
      {
        type: 'warning',
        confirmButtonText: '确认执行',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }

  triggering.value = true
  try {
    const res: any = await triggerArchive()
    const r = res?.data || res
    if (r?.status === 'SUCCESS') {
      ElMessage.success(
        `归档完成，迁移 ${r.migratedCount} 条任务，耗时 ${r.costMillis}ms`
      )
      await loadList()
      await loadPendingCount()
    } else if (r?.status === 'SKIPPED') {
      ElMessage.warning(r.message || '归档被跳过')
    } else {
      ElMessage.error(r?.message || '归档失败')
    }
  } catch (e: any) {
    ElMessage.error('归档失败: ' + (e?.message || ''))
  } finally {
    triggering.value = false
  }
}

// 工具函数
const priorityType = (p?: number) => {
  switch (p) {
    case 1: return 'danger'
    case 2: return 'warning'
    case 3: return 'info'
    case 4: return 'success'
    default: return 'info'
  }
}

const priorityLabel = (p?: number) => {
  switch (p) {
    case 1: return '最高'
    case 2: return '高'
    case 3: return '中'
    case 4: return '低'
    default: return '-'
  }
}

const statusType = (s?: string) => {
  return s === 'COMPLETED' ? 'success' : 'info'
}

const statusLabel = (s?: string) => {
  if (s === 'COMPLETED') return '已完成'
  if (s === 'WITHDRAWN') return '已撤回'
  return s || '-'
}

onMounted(() => {
  loadList()
  loadPendingCount()
})
</script>

<style scoped>
.history-container {
  max-width: 1400px;
  margin: 0 auto;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header h3 {
  margin: 0;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.filter-form {
  margin-bottom: 16px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.table-scroll-wrapper {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

/* 移动端：日期选择器上下布局 + 筛选表单整行竖排 + 头部 actions 竖排 */
@media (max-width: 768px) {
  .header {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }

  .header-actions {
    justify-content: flex-end;
  }

  :deep(.filter-form .el-form-item) {
    display: block;
    margin-right: 0;
  }

  :deep(.filter-form .el-form-item .el-input),
  :deep(.filter-form .el-form-item .el-select),
  :deep(.filter-form .el-form-item .el-date-editor) {
    width: 100% !important;
    max-width: 100%;
  }

  :deep(.el-dialog) {
    width: 92vw !important;
  }
}
</style>
