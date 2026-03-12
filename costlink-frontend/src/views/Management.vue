<template>
  <div class="management">
    <h2 class="page-title">报销管理</h2>

    <el-card>
      <div class="toolbar">
        <el-radio-group v-model="statusFilter" @change="loadData">
          <el-radio-button label="">全部</el-radio-button>
          <el-radio-button label="PENDING">待审核</el-radio-button>
          <el-radio-button label="CONFIRMED">待付款</el-radio-button>
          <el-radio-button label="PAID">已付款</el-radio-button>
          <el-radio-button label="REJECTED">已驳回</el-radio-button>
        </el-radio-group>

        <div>
          <el-button
            type="danger"
            :disabled="deletableIds.length === 0"
            @click="handleBatchDelete"
          >
            批量删除 ({{ deletableIds.length }})
          </el-button>
          <el-button
            type="primary"
            :disabled="selectedIds.length === 0"
            @click="handleExport"
          >
            <el-icon><Download /></el-icon>
            导出选中图片
          </el-button>
        </div>
      </div>

      <el-table
        :data="records"
        v-loading="loading"
        stripe
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="displayName" label="申请人" width="120">
          <template #default="{ row }">
            {{ row.displayName || row.username }}
          </template>
        </el-table-column>
        <el-table-column label="有效期" width="200">
          <template #default="{ row }">
            {{ formatDate(row.vpnStartDate) }} ~ {{ formatDate(row.vpnEndDate) }}
          </template>
        </el-table-column>
        <el-table-column prop="totalAmount" label="金额" width="100">
          <template #default="{ row }">
            ¥{{ row.totalAmount }}
          </template>
        </el-table-column>
        <el-table-column prop="alipayAccount" label="真实姓名" width="150" />
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
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button type="text" @click="viewDetail(row)">详情</el-button>
            <template v-if="row.status === 'PENDING'">
              <el-button type="text" @click="handleConfirm(row)">确认</el-button>
              <el-button type="text" @click="handleReject(row)">驳回</el-button>
            </template>
            <template v-if="row.status === 'CONFIRMED'">
              <el-button type="text" @click="handlePay(row)">标记已付款</el-button>
            </template>
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
          <el-descriptions-item label="有效期">
            {{ formatDate(currentRecord.vpnStartDate) }} ~ {{ formatDate(currentRecord.vpnEndDate) }}
          </el-descriptions-item>
          <el-descriptions-item label="总金额">¥{{ currentRecord.totalAmount }}</el-descriptions-item>
          <el-descriptions-item label="申请时间">{{ formatDateTime(currentRecord.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="真实姓名">{{ currentRecord.alipayAccount || '-' }}</el-descriptions-item>
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

    <!-- Reject Dialog -->
    <el-dialog v-model="rejectDialogVisible" title="驳回报销" width="400px">
      <el-form>
        <el-form-item label="驳回原因">
          <el-input
            v-model="rejectReason"
            type="textarea"
            :rows="3"
            placeholder="请输入驳回原因"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import {
  getAllReimbursements,
  getReimbursementDetail,
  confirmReimbursement,
  rejectReimbursement,
  markAsPaid,
  deleteReimbursements,
  exportImages
} from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

interface ReimbursementRecord {
  id: number
  userId: number
  username: string
  displayName: string
  alipayAccount: string
  totalAmount: number
  vpnStartDate: string
  vpnEndDate: string
  status: string
  rejectReason: string
  createdAt: string
  images: Array<{
    id: number
    imagePath: string
    ocrAmount: number
  }>
}

const route = useRoute()
const loading = ref(false)
const records = ref<ReimbursementRecord[]>([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const statusFilter = ref('')
const selectedIds = ref<number[]>([])
const deletableIds = ref<number[]>([])
const selectedTotalAmount = ref(0)
const dialogVisible = ref(false)
const currentRecord = ref<ReimbursementRecord | null>(null)
const rejectDialogVisible = ref(false)
const rejectReason = ref('')
const rejectingId = ref<number | null>(null)

onMounted(() => {
  if (route.query.status) {
    statusFilter.value = route.query.status as string
  }
  loadData()
})

async function loadData() {
  loading.value = true
  try {
    const res = await getAllReimbursements(page.value, pageSize.value, statusFilter.value)
    records.value = res.data.records
    total.value = res.data.total
  } catch (error) {
    console.error('Failed to load reimbursements', error)
  } finally {
    loading.value = false
  }
}

function handleSelectionChange(selection: ReimbursementRecord[]) {
  const paidRecords = selection.filter(r => r.status === 'PAID')
  selectedIds.value = paidRecords.map(r => r.id)
  selectedTotalAmount.value = paidRecords.reduce((sum, r) => sum + r.totalAmount, 0)
  deletableIds.value = selection
    .filter(r => r.status === 'REJECTED' || r.status === 'PAID')
    .map(r => r.id)
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

async function handleConfirm(row: ReimbursementRecord) {
  await ElMessageBox.confirm('确定要确认此报销申请吗？', '确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'info'
  })

  try {
    await confirmReimbursement(row.id)
    ElMessage.success('确认成功')
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

function handleReject(row: ReimbursementRecord) {
  rejectingId.value = row.id
  rejectReason.value = ''
  rejectDialogVisible.value = true
}

async function confirmReject() {
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请输入驳回原因')
    return
  }

  try {
    await rejectReimbursement(rejectingId.value!, rejectReason.value)
    ElMessage.success('驳回成功')
    rejectDialogVisible.value = false
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

async function handlePay(row: ReimbursementRecord) {
  await ElMessageBox.confirm(
    `确定已向 ${row.displayName || row.username}（${row.alipayAccount}）转账 ¥${row.totalAmount} 吗？`,
    '确认付款',
    {
      confirmButtonText: '确认已付款',
      cancelButtonText: '取消',
      type: 'warning'
    }
  )

  try {
    await markAsPaid(row.id)
    ElMessage.success('标记成功')
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

async function handleBatchDelete() {
  await ElMessageBox.confirm(
    `确定要删除选中的 ${deletableIds.value.length} 条记录吗？此操作不可恢复。`,
    '批量删除',
    {
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      type: 'warning'
    }
  )

  try {
    await deleteReimbursements(deletableIds.value)
    ElMessage.success('删除成功')
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '删除失败')
  }
}

async function handleExport() {
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请先选择已付款的记录')
    return
  }

  try {
    const response = await exportImages(selectedIds.value)
    const blob = new Blob([response as any], { type: 'application/zip' })
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `reimbursements_${new Date().toISOString().split('T')[0]}_${selectedTotalAmount.value}元.zip`
    a.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error('导出失败')
  }
}

function formatDate(dateStr: string) {
  if (!dateStr) return '-'
  return dateStr.split('T')[0]
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
.management {
  padding: 0;
}

.page-title {
  margin-bottom: 24px;
  font-size: 20px;
  color: #303133;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
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
