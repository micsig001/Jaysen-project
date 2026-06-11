<template>
  <div class="visualization-container">
    <el-card>
      <template #header>
        <div class="header">
          <h3>任务关系可视化</h3>
          <div class="header-actions">
            <el-radio-group v-model="mode" @change="handleModeChange">
              <el-radio-button value="radiation">单人辐射图</el-radio-button>
              <el-radio-button value="multi">多人全景图</el-radio-button>
            </el-radio-group>
            <el-button type="primary" :loading="loading" @click="reload">刷新</el-button>
          </div>
        </div>
      </template>

      <!-- 辐射图：选择中心用户 -->
      <div v-if="mode === 'radiation'" class="controls">
        <el-form :inline="true">
          <el-form-item label="中心用户">
            <el-input v-model="centerUserId" placeholder="请输入 UserID" style="width: 240px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadRadiation">生成辐射图</el-button>
          </el-form-item>
        </el-form>
        <p class="tip">说明：以该用户为中心，展示与之有任务往来的所有人员（最多 50 人，按任务数排序）</p>
      </div>

      <!-- 多人全景图：勾选人员 -->
      <div v-else class="controls">
        <el-form :inline="true">
          <el-form-item label="用户ID（多个用逗号分隔）">
            <el-input
              v-model="multiUserIds"
              placeholder="user1,user2,user3"
              style="width: 360px"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadMultiView">生成全景图</el-button>
          </el-form-item>
        </el-form>
        <p class="tip">说明：展示这些人之间所有任务流转关系（最多 50 人）</p>
      </div>

      <!-- ECharts 图表 / loading / 空状态 -->
      <div v-if="loading" class="chart-skeleton">
        <el-skeleton :rows="8" animated />
        <p class="loading-tip">正在加载关系数据...</p>
      </div>
      <div
        v-else-if="!hasGraph"
        class="chart-empty"
      >
        <el-empty
          :description="mode === 'radiation' ? '请输入中心用户 UserID 并点击生成' : '请输入至少 2 个用户 ID 并点击生成'"
          :image-size="100"
        />
      </div>
      <div v-show="!loading && hasGraph" ref="chartRef" class="chart-container"></div>

      <!-- 统计信息 -->
      <div v-if="hasGraph && stats" class="stats">
        <el-tag>节点数: {{ stats.nodes }}</el-tag>
        <el-tag>边数: {{ stats.links }}</el-tag>
        <el-tag v-if="stats.truncated" type="warning">已截断（超过 50 人）</el-tag>
        <el-tag>生成时间: {{ stats.generatedAt }}</el-tag>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { getRadiationGraph, getMultiViewGraph } from '@/api/visualization'
import { useUserStore } from '@/stores/user'
import type { EChartsNode, EChartsLink } from '@/types/api'

const userStore = useUserStore()
const mode = ref<'radiation' | 'multi'>('radiation')
const centerUserId = ref(userStore.userInfo?.userId || '')
const multiUserIds = ref('')
const loading = ref(false)

const chartRef = ref<HTMLElement | null>(null)
let chartInstance: echarts.ECharts | null = null

const stats = reactive({
  nodes: 0,
  links: 0,
  truncated: false,
  generatedAt: '',
})

// 是否已生成过图（控制空状态展示）
const hasGraph = computed(() => stats.nodes > 0 || stats.links > 0)

const handleModeChange = () => {
  // 切换模式时清空图表
  if (chartInstance) chartInstance.clear()
}

const initChart = () => {
  if (!chartRef.value) return
  chartInstance = echarts.init(chartRef.value)
}

// P2.1: GraphData 接口与后端 VisualizationController 返回对齐
interface GraphData {
  nodes: EChartsNode[]
  links: EChartsLink[]
  truncated?: boolean
  generatedAt?: string
}

const renderGraph = (data: GraphData) => {
  if (!chartInstance) return
  const option = {
    tooltip: {
      formatter: (params: echarts.DefaultLabelFormatterCallbackParams) => {
        if (params.dataType === 'node') {
          return `<b>${params.name}</b><br/>任务数: ${(params.value as number) || 0}`
        }
        if (params.dataType === 'edge') {
          const edgeData = params.data as EChartsLink
          return `${edgeData.source} → ${edgeData.target}<br/>任务数: ${edgeData.value || 0}`
        }
        return ''
      },
    },
    legend: [
      {
        data: ['中心用户', '关联人员'],
        orient: 'vertical',
        left: 10,
        top: 20,
      },
    ],
    series: [
      {
        type: 'graph',
        layout: 'force',
        roam: true,
        draggable: true,
        categories: [{ name: '中心用户' }, { name: '关联人员' }],
        force: {
          repulsion: 200,
          edgeLength: [50, 100],
        },
        data: data.nodes || [],
        links: data.links || [],
        label: {
          show: true,
          position: 'right',
          formatter: '{b}',
        },
        edgeSymbol: ['none', 'arrow'],
        edgeSymbolSize: 8,
        lineStyle: {
          color: '#aaa',
          curveness: 0.1,
        },
        emphasis: {
          focus: 'adjacency',
          lineStyle: { width: 4 },
        },
      },
    ],
  }
  chartInstance.setOption(option, true)
  // 更新统计
  stats.nodes = (data.nodes || []).length
  stats.links = (data.links || []).length
  stats.truncated = !!data.truncated
  stats.generatedAt = data.generatedAt || new Date().toISOString()
}

const loadRadiation = async () => {
  if (!centerUserId.value) {
    ElMessage.warning('请输入中心用户 UserID')
    return
  }
  loading.value = true
  try {
    const res: any = await getRadiationGraph(centerUserId.value)
    await nextTick()
    if (!chartInstance) initChart()
    renderGraph(res)
  } catch (e: any) {
    ElMessage.error('加载辐射图失败: ' + (e?.message || ''))
  } finally {
    loading.value = false
  }
}

const loadMultiView = async () => {
  const ids = multiUserIds.value
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
  if (ids.length < 2) {
    ElMessage.warning('请至少输入 2 个用户 ID')
    return
  }
  loading.value = true
  try {
    const res: any = await getMultiViewGraph({ userIds: ids })
    await nextTick()
    if (!chartInstance) initChart()
    renderGraph(res)
  } catch (e: any) {
    ElMessage.error('加载全景图失败: ' + (e?.message || ''))
  } finally {
    loading.value = false
  }
}

const reload = () => {
  if (mode.value === 'radiation') loadRadiation()
  else loadMultiView()
}

const handleResize = () => {
  chartInstance?.resize()
}

onMounted(() => {
  nextTick(() => {
    initChart()
    if (centerUserId.value) loadRadiation()
  })
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
})
</script>

<style scoped>
.visualization-container {
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

.controls {
  margin-bottom: 16px;
}

.tip {
  color: #909399;
  font-size: 12px;
  margin-top: -8px;
  margin-bottom: 16px;
}

.chart-container {
  width: 100%;
  height: 600px;
  background: #fafafa;
  border-radius: 4px;
}

.chart-skeleton {
  width: 100%;
  height: 600px;
  background: #fafafa;
  border-radius: 4px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px;
  box-sizing: border-box;
}

.chart-skeleton .loading-tip {
  margin-top: 16px;
  color: #909399;
  font-size: 13px;
}

.chart-empty {
  width: 100%;
  height: 600px;
  background: #fafafa;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stats {
  display: flex;
  gap: 12px;
  margin-top: 16px;
  flex-wrap: wrap;
}

/* 移动端适配：图表高度降低，操作按钮换行 */
@media (max-width: 768px) {
  .header {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }

  .header-actions {
    justify-content: flex-end;
    flex-wrap: wrap;
    gap: 8px;
  }

  .header-actions :deep(.el-radio-group) {
    width: 100%;
  }

  .header-actions :deep(.el-radio-button) {
    flex: 1;
  }

  .controls :deep(.el-form) {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .controls :deep(.el-form-item) {
    margin-right: 0;
    display: flex;
    flex-direction: column;
    align-items: stretch;
  }

  .controls :deep(.el-form-item .el-input),
  .controls :deep(.el-form-item .el-button) {
    width: 100% !important;
    max-width: 100%;
  }

  .chart-container,
  .chart-skeleton,
  .chart-empty {
    height: 360px;
  }

  .stats {
    font-size: 12px;
  }
}
</style>
