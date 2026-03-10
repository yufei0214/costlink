<template>
  <div class="dashboard">
    <h2 class="page-title">管理员仪表盘</h2>

    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon total">
              <el-icon size="32"><Document /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.totalApplications }}</div>
              <div class="stat-label">总申请数</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card class="stat-card clickable" @click="goToManagement('PENDING')">
          <div class="stat-content">
            <div class="stat-icon pending">
              <el-icon size="32"><Clock /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.pendingCount }}</div>
              <div class="stat-label">待审核</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card class="stat-card clickable" @click="goToManagement('CONFIRMED')">
          <div class="stat-content">
            <div class="stat-icon confirmed">
              <el-icon size="32"><Check /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.confirmedCount }}</div>
              <div class="stat-label">待付款</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon paid">
              <el-icon size="32"><CircleCheck /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.paidCount }}</div>
              <div class="stat-label">已付款</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getStatistics } from '@/api'

const router = useRouter()

const stats = reactive({
  totalApplications: 0,
  pendingCount: 0,
  confirmedCount: 0,
  paidCount: 0,
  rejectedCount: 0
})

onMounted(async () => {
  await loadStatistics()
})

async function loadStatistics() {
  try {
    const res = await getStatistics()
    Object.assign(stats, res.data)
  } catch (error) {
    console.error('Failed to load statistics', error)
  }
}

function goToManagement(status: string) {
  router.push({ path: '/management', query: { status } })
}
</script>

<style scoped>
.dashboard {
  padding: 0;
}

.page-title {
  margin-bottom: 24px;
  font-size: 20px;
  color: #303133;
}

.stats-row {
  margin-bottom: 24px;
}

.stat-card {
  cursor: default;
}

.stat-card.clickable {
  cursor: pointer;
  transition: transform 0.2s;
}

.stat-card.clickable:hover {
  transform: translateY(-4px);
}

.stat-content {
  display: flex;
  align-items: center;
  padding: 10px;
}

.stat-icon {
  width: 64px;
  height: 64px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 16px;
}

.stat-icon.total {
  background-color: #e8f4fd;
  color: #409eff;
}

.stat-icon.pending {
  background-color: #fdf6ec;
  color: #e6a23c;
}

.stat-icon.confirmed {
  background-color: #f0f9eb;
  color: #67c23a;
}

.stat-icon.paid {
  background-color: #ecf5ff;
  color: #409eff;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 4px;
}
</style>
