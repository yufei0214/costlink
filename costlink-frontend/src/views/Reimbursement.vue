<template>
  <div class="reimbursement">
    <h2 class="page-title">我的报销</h2>

    <el-card>
      <el-table :data="records" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="reimbursementMonth" label="报销月份" width="120" />
        <el-table-column prop="totalAmount" label="金额" width="100">
          <template #default="{ row }">
            ¥{{ row.totalAmount }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="申请时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column prop="rejectReason" label="驳回原因">
          <template #default="{ row }">
            {{ row.rejectReason || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="text" @click="viewDetail(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadData"
          @size-change="loadData"
        />
      </div>
    </el-card>

    <!-- Detail Dialog -->
    <el-dialog v-model="dialogVisible" title="报销详情" width="600px">
      <div v-if="currentRecord" class="detail-content">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="申请人">{{ currentRecord.displayName || currentRecord.username }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusType(currentRecord.status)">
              {{ getStatusLabel(currentRecord.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="报销月份">{{ currentRecord.reimbursementMonth }}</el-descriptions-item>
          <el-descriptions-item label="总金额">¥{{ currentRecord.totalAmount }}</el-descriptions-item>
          <el-descriptions-item label="申请时间">{{ formatDateTime(currentRecord.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="真实姓名">{{ currentRecord.alipayAccount || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="currentRecord.remark" label="备注说明" :span="2">
            {{ currentRecord.remark }}
          </el-descriptions-item>
          <el-descriptions-item v-if="currentRecord.rejectReason" label="驳回原因" :span="2">
            {{ currentRecord.rejectReason }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="images-section" v-if="currentRecord.images?.length">
          <h4>购买截图</h4>
          <div class="images-grid">
            <div v-for="img in currentRecord.images" :key="img.id" class="image-item">
              <el-image
                :src="'/api/uploads/' + img.imagePath"
                :preview-src-list="['/api/uploads/' + img.imagePath]"
                fit="cover"
              />
              <div class="image-amount">识别金额: ¥{{ img.ocrAmount || 0 }}</div>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getMyReimbursements, getReimbursementDetail } from '@/api'

interface ReimbursementRecord {
  id: number
  userId: number
  username: string
  displayName: string
  alipayAccount: string
  totalAmount: number
  reimbursementMonth: string
  remark: string
  status: string
  rejectReason: string
  createdAt: string
  images: Array<{
    id: number
    imagePath: string
    ocrAmount: number
  }>
}

const loading = ref(false)
const records = ref<ReimbursementRecord[]>([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dialogVisible = ref(false)
const currentRecord = ref<ReimbursementRecord | null>(null)

onMounted(() => {
  loadData()
})

async function loadData() {
  loading.value = true
  try {
    const res = await getMyReimbursements(page.value, pageSize.value)
    records.value = res.data.records
    total.value = res.data.total
  } catch (error) {
    console.error('Failed to load reimbursements', error)
  } finally {
    loading.value = false
  }
}

async function viewDetail(row: ReimbursementRecord) {
  try {
    const res = await getReimbursementDetail(row.id)
    currentRecord.value = res.data
    dialogVisible.value = true
  } catch (error) {
    console.error('Failed to load detail', error)
  }
}

function formatDateTime(dateStr: string) {
  if (!dateStr) return '-'
  return dateStr.replace('T', ' ').substring(0, 19)
}

function getStatusType(status: string) {
  const map: Record<string, string> = {
    PENDING: 'warning',
    CONFIRMED: 'success',
    PAID: 'success',
    REJECTED: 'danger'
  }
  return map[status] || 'info'
}

function getStatusLabel(status: string) {
  const map: Record<string, string> = {
    PENDING: '待审核',
    CONFIRMED: '已确认',
    PAID: '已付款',
    REJECTED: '已驳回'
  }
  return map[status] || status
}
</script>

<style scoped>
.reimbursement {
  padding: 0;
}

.page-title {
  margin-bottom: 24px;
  font-size: 20px;
  color: #303133;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.detail-content {
  padding: 0;
}

.images-section {
  margin-top: 20px;
}

.images-section h4 {
  margin-bottom: 12px;
  color: #606266;
}

.images-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.image-item {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  overflow: hidden;
}

.image-item .el-image {
  width: 100%;
  height: 120px;
}

.image-amount {
  padding: 8px;
  font-size: 12px;
  color: #606266;
  text-align: center;
  background: #f5f7fa;
}
</style>
