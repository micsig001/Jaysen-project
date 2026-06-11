<template>
  <div class="admin-container">
    <!-- 顶部统计卡片 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :xs="24" :sm="12" :md="8">
        <el-card shadow="hover" class="stat-card stat-employee">
          <div class="stat-label">普通员工</div>
          <div v-if="statsLoading" class="stat-value">
            <el-skeleton-item variant="text" style="width: 60%; height: 32px" />
          </div>
          <div v-else class="stat-value">{{ statsMap.EMPLOYEE ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="8">
        <el-card shadow="hover" class="stat-card stat-manager">
          <div class="stat-label">部门经理</div>
          <div v-if="statsLoading" class="stat-value">
            <el-skeleton-item variant="text" style="width: 60%; height: 32px" />
          </div>
          <div v-else class="stat-value">{{ statsMap.MANAGER ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="8">
        <el-card shadow="hover" class="stat-card stat-admin">
          <div class="stat-label">超级管理员</div>
          <div v-if="statsLoading" class="stat-value">
            <el-skeleton-item variant="text" style="width: 60%; height: 32px" />
          </div>
          <div v-else class="stat-value">{{ statsMap.ADMIN ?? 0 }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 用户列表卡片 -->
    <el-card class="user-card">
      <template #header>
        <div class="card-header">
          <h3>用户列表</h3>
        </div>
      </template>

      <!-- 筛选栏 -->
      <div class="filter-bar">
        <el-input
          v-model="filters.keyword"
          placeholder="搜索 UserID / 姓名 / 手机 / 邮箱"
          clearable
          style="width: 280px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select
          v-model="filters.role"
          placeholder="角色"
          clearable
          style="width: 140px"
          @change="handleSearch"
        >
          <el-option label="普通员工" value="EMPLOYEE" />
          <el-option label="部门经理" value="MANAGER" />
          <el-option label="超级管理员" value="ADMIN" />
        </el-select>
        <el-select
          v-model="filters.status"
          placeholder="状态"
          clearable
          style="width: 120px"
          @change="handleSearch"
        >
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <!-- 表格 -->
      <div class="table-scroll-wrapper">
        <el-table
          v-loading="loading"
          :data="userList"
          border
          stripe
          style="width: 100%; margin-top: 16px"
          :header-cell-style="{ background: '#fafafa' }"
        >
          <template #empty>
            <el-empty
              v-if="!loading"
              description="暂无用户数据，试试调整筛选条件"
              :image-size="80"
            />
          </template>
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column prop="userId" label="UserID" width="140" />
        <el-table-column label="角色" width="120">
          <template #default="{ row }">
            <el-tag :type="getRoleTagType(row.role)" size="small">
              {{ getRoleLabel(row.role) }}
            </el-tag>
            <el-tooltip
              v-if="row.manualRole"
              content="该角色由管理员手动设置，企微同步不会覆盖"
              placement="top"
            >
              <el-icon class="manual-icon"><Lock /></el-icon>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="departmentName" label="部门" min-width="120">
          <template #default="{ row }">
            <span v-if="row.departmentName">{{ row.departmentName }}</span>
            <span v-else class="muted">未关联</span>
          </template>
        </el-table-column>
        <el-table-column prop="position" label="职位" min-width="120">
          <template #default="{ row }">
            <span v-if="row.position">{{ row.position }}</span>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最后同步" width="170">
          <template #default="{ row }">
            <span v-if="row.lastSyncTime">{{ formatTime(row.lastSyncTime) }}</span>
            <span v-else class="muted">未同步</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="['ADMIN']"
              size="small"
              type="primary"
              link
              @click="openChangeRoleDialog(row)"
            >
              改角色
            </el-button>
            <el-button
              v-if="row.status === 1"
              v-permission="['ADMIN']"
              size="small"
              type="danger"
              link
              :disabled="row.role === 'ADMIN'"
              @click="handleDisable(row)"
            >
              禁用
            </el-button>
            <el-button
              v-else
              v-permission="['ADMIN']"
              size="small"
              type="success"
              link
              @click="handleEnable(row)"
            >
              启用
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      </div>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        style="margin-top: 16px; text-align: right"
        @size-change="loadUsers"
        @current-change="loadUsers"
      />
    </el-card>

    <!-- 改角色弹窗 -->
    <el-dialog
      v-model="roleDialogVisible"
      title="修改用户角色"
      width="420px"
      :close-on-click-modal="false"
    >
      <el-form label-width="80px">
        <el-form-item label="用户">
          <span>{{ selectedUser?.name }}（{{ selectedUser?.userId }}）</span>
        </el-form-item>
        <el-form-item label="当前角色">
          <el-tag :type="getRoleTagType(selectedUser?.role)">
            {{ getRoleLabel(selectedUser?.role) }}
          </el-tag>
        </el-form-item>
        <el-form-item label="新角色">
          <el-select v-model="newRole" placeholder="选择新角色" style="width: 100%">
            <el-option label="普通员工" value="EMPLOYEE" />
            <el-option label="部门经理" value="MANAGER" />
            <el-option label="超级管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
        >
          <template #title>
            修改后该用户的角色将被标记为"手动设置"，企微日常同步不会覆盖此修改
          </template>
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmChangeRole">
          确认修改
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Lock } from '@element-plus/icons-vue'
import {
  listUsers,
  getRoleStats,
  changeUserRole,
  disableUser,
  enableUser
} from '@/api/user'
import { ROLE, ROLE_LABELS, ROLE_TAG_TYPES, type Role } from '@/utils/labels'
import type { UserVO } from '@/types/api'
import type { RoleStatsVO } from '@/api/user'

// 筛选条件
const filters = reactive({
  keyword: '',
  role: '' as '' | Role,
  status: undefined as number | undefined
})

// 分页
const pagination = reactive({
  pageNum: 1,
  pageSize: 10
})

const loading = ref(false)
const statsLoading = ref(true)
const submitting = ref(false)
const userList = ref<any[]>([])
const total = ref(0)
const roleStats = ref<{ role: string; count: number }[]>([])

// 角色统计 map（EMPLOYEE / MANAGER / ADMIN → count）
// P2.1: 收紧 Record<string, number> → RoleStatsVO
const statsMap = computed<RoleStatsVO>(() => {
  const m: RoleStatsVO = {}
  for (const item of roleStats.value) {
    m[item.role] = item.count
  }
  return m
})

// 改角色弹窗
const roleDialogVisible = ref(false)
// P2.1: 收紧 ref<any> → ref<UserVO | null>
const selectedUser = ref<UserVO | null>(null)
const newRole = ref<Role>(ROLE.EMPLOYEE)
function getRoleLabel(role?: string) {
  return role ? (ROLE_LABELS[role as Role] ?? role) : '-'
}
function getRoleTagType(role?: string) {
  return role ? (ROLE_TAG_TYPES[role as Role] ?? '') : ''
}

// 工具：时间
function formatTime(s?: string) {
  if (!s) return '-'
  return s.replace('T', ' ').slice(0, 19)
}

// 加载用户列表
const loadUsers = async () => {
  loading.value = true
  try {
    const res: any = await listUsers({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      keyword: filters.keyword || undefined,
      role: filters.role || undefined,
      status: filters.status as 0 | 1 | undefined
    })
    userList.value = res.records || []
    total.value = res.total || 0
  } catch (e: any) {
    ElMessage.error('加载用户列表失败: ' + (e?.message || ''))
    userList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// 加载角色统计
const loadRoleStats = async () => {
  statsLoading.value = true
  try {
    const res: any = await getRoleStats()
    roleStats.value = Array.isArray(res) ? res : []
  } catch {
    roleStats.value = []
  } finally {
    statsLoading.value = false
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadUsers()
}

const handleReset = () => {
  filters.keyword = ''
  filters.role = ''
  filters.status = undefined
  handleSearch()
}

// 打开改角色弹窗
const openChangeRoleDialog = (row: any) => {
  selectedUser.value = row
  newRole.value = row.role
  roleDialogVisible.value = true
}

// 确认改角色
const confirmChangeRole = async () => {
  if (!selectedUser.value) return
  if (newRole.value === selectedUser.value.role) {
    ElMessage.info('角色未变化')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定将用户「${selectedUser.value.name}」的角色修改为「${getRoleLabel(newRole.value)}」？`,
      '二次确认',
      { type: 'warning' }
    )
  } catch {
    return // 用户取消
  }
  submitting.value = true
  try {
    await changeUserRole(selectedUser.value.userId, newRole.value)
    ElMessage.success('角色修改成功')
    roleDialogVisible.value = false
    await loadUsers()
    await loadRoleStats()
  } catch (e: any) {
    ElMessage.error('修改失败: ' + (e?.message || ''))
  } finally {
    submitting.value = false
  }
}

// 禁用
const handleDisable = async (row: any) => {
  try {
    await ElMessageBox.confirm(
      `确定禁用用户「${row.name}」？禁用后该用户将无法登录`,
      '禁用账号',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await disableUser(row.userId)
    ElMessage.success('已禁用')
    await loadUsers()
  } catch (e: any) {
    ElMessage.error('禁用失败: ' + (e?.message || ''))
  }
}

// 启用
const handleEnable = async (row: any) => {
  try {
    await enableUser(row.userId)
    ElMessage.success('已启用')
    await loadUsers()
  } catch (e: any) {
    ElMessage.error('启用失败: ' + (e?.message || ''))
  }
}

onMounted(async () => {
  await Promise.all([loadUsers(), loadRoleStats()])
})
</script>

<style scoped>
.admin-container {
  max-width: 1280px;
  margin: 0 auto;
}

.stat-row {
  margin-bottom: 16px;
}

.stat-card {
  text-align: center;
  border-radius: 8px;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 32px;
  font-weight: 600;
  color: #303133;
}

.stat-employee .stat-value { color: #409eff; }
.stat-manager .stat-value  { color: #e6a23c; }
.stat-admin .stat-value    { color: #f56c6c; }

.user-card {
  border-radius: 8px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-bar {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.muted {
  color: #c0c4cc;
  font-size: 12px;
}

.manual-icon {
  margin-left: 4px;
  color: #909399;
  cursor: help;
  vertical-align: middle;
}

/* 移动端适配：表格横向滚动 + 筛选栏整行竖排 */
.table-scroll-wrapper {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

@media (max-width: 768px) {
  .stat-row .el-col {
    margin-bottom: 12px;
  }

  .stat-value {
    font-size: 28px;
  }

  .filter-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-bar .el-input,
  .filter-bar .el-select,
  .filter-bar .el-button {
    width: 100% !important;
  }
}
</style>
