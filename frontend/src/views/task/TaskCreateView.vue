<template>
  <div class="task-create-container">
    <el-card>
      <template #header>
        <h2>新建任务</h2>
      </template>

      <el-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        label-width="100px"
      >
        <el-form-item label="任务标题" prop="title">
          <el-input v-model="formData.title" placeholder="请输入任务标题" />
        </el-form-item>

        <el-form-item label="任务描述" prop="description">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="4"
            placeholder="请输入任务描述"
          />
        </el-form-item>

        <el-form-item label="优先级" prop="priority">
          <el-select v-model="formData.priority" placeholder="请选择优先级">
            <el-option label="最高" :value="1" />
            <el-option label="高" :value="2" />
            <el-option label="中" :value="3" />
            <el-option label="低" :value="4" />
          </el-select>
        </el-form-item>

        <el-form-item label="执行方" prop="assigneeId">
          <el-input v-model="formData.assigneeId" placeholder="请输入执行方 UserID" />
        </el-form-item>

        <el-form-item label="来源备注">
          <el-input v-model="formData.sourceRemark" placeholder="请输入来源备注" />
        </el-form-item>

        <el-form-item label="预计耗时(分钟)">
          <el-input-number
            v-model="formData.estimatedDuration"
            :min="1"
            :max="43200"
            placeholder="请输入预计耗时"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSubmit" :loading="submitting">
            创建任务
          </el-button>
          <el-button @click="handleCancel">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createTask } from '@/api/task'
import { PRIORITY } from '@/utils/labels'
import type { Priority } from '@/types/api'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const submitting = ref(false)

const formData = reactive<{
  title: string
  description: string
  priority: Priority
  assigneeId: string
  sourceRemark: string
  estimatedDuration: number | undefined
  creatorId: string
}>({
  title: '',
  description: '',
  priority: PRIORITY.MEDIUM,
  assigneeId: '',
  sourceRemark: '',
  estimatedDuration: undefined,
  // 修复（H2）：从 userStore 获取当前用户 UserID，避免后端 NOT NULL 约束失败
  creatorId: userStore.userInfo?.userId || ''
})

const rules = {
  title: [{ required: true, message: '请输入任务标题', trigger: 'blur' }],
  priority: [{ required: true, message: '请选择优先级', trigger: 'change' }],
  assigneeId: [{ required: true, message: '请输入执行方 UserID', trigger: 'blur' }]
}

const handleSubmit = async () => {
  try {
    await formRef.value?.validate()

    // 提交前再次兜底：确保 creatorId 一定有值
    if (!formData.creatorId) {
      formData.creatorId = userStore.userInfo?.userId || ''
    }
    if (!formData.creatorId) {
      ElMessage.error('无法获取当前用户信息，请重新登录')
      return
    }

    submitting.value = true
    await createTask(formData)

    ElMessage.success('任务创建成功')
    router.push('/tasks')
  } catch (error: any) {
    if (error.message) {
      ElMessage.error(error.message)
    }
  } finally {
    submitting.value = false
  }
}

const handleCancel = () => {
  router.back()
}
</script>

<style scoped>
.task-create-container {
  padding: 16px;
  min-height: 100vh;
  background: #f5f5f5;
}

.el-card {
  max-width: 800px;
  margin: 0 auto;
}

@media (max-width: 768px) {
  .task-create-container {
    padding: 12px;
  }

  .el-card {
    max-width: 100%;
  }
}
</style>
