<template>
  <div class="apply">
    <h2 class="page-title">报销申请</h2>

    <el-card class="apply-card">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="真实姓名" required>
          <el-input
            v-model="realName"
            placeholder="请输入您的真实姓名（用于转账）"
            style="width: 300px"
          />
          <div v-if="!realName.trim()" class="form-tip">提交报销前需填写真实姓名</div>
        </el-form-item>

        <el-form-item label="有效期" required>
          <el-col :span="11">
            <el-form-item prop="vpnStartDate">
              <el-date-picker
                v-model="form.vpnStartDate"
                type="date"
                placeholder="开始日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="2" style="text-align: center">至</el-col>
          <el-col :span="11">
            <el-form-item prop="vpnEndDate">
              <el-date-picker
                v-model="form.vpnEndDate"
                type="date"
                placeholder="结束日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-form-item>

        <el-form-item label="购买记录截图" required>
          <el-upload
            v-model:file-list="fileList"
            :action="uploadUrl"
            :headers="uploadHeaders"
            :on-success="handleUploadSuccess"
            :on-error="handleUploadError"
            :on-remove="handleRemove"
            :on-preview="handlePreview"
            :before-upload="beforeUpload"
            list-type="picture-card"
            accept="image/*"
          >
            <el-icon><Plus /></el-icon>
            <template #tip>
              <div class="el-upload__tip">支持jpg/png格式，单个文件不超过10MB</div>
            </template>
          </el-upload>

          <el-image-viewer
            v-if="previewVisible"
            :url-list="[previewUrl]"
            @close="previewVisible = false"
          />
        </el-form-item>

        <el-form-item label="识别金额">
          <div class="amount-list" v-if="uploadedImages.length > 0">
            <div v-for="(img, index) in uploadedImages" :key="index" class="amount-item">
              <span>图片{{ index + 1 }}：</span>
              <el-input-number
                v-model="img.ocrAmount"
                :precision="2"
                :min="0"
                :max="99999"
                size="small"
              />
              <span> 元</span>
              <span v-if="img.ocrAmount === 0" class="amount-tip">未识别到金额，请手动输入</span>
            </div>
          </div>
          <div v-else class="no-images">请先上传购买截图</div>
        </el-form-item>

        <el-form-item label="总金额">
          <el-input-number
            v-model="form.totalAmount"
            :precision="2"
            :min="0"
            :max="99999"
            size="large"
          />
          <span style="margin-left: 8px">元</span>
          <el-button type="text" @click="calculateTotal" style="margin-left: 16px">
            自动累加
          </el-button>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="submitting"
            :disabled="!canSubmit"
            @click="handleSubmit"
          >
            提交报销申请
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { createReimbursement, updateAlipayAccount } from '@/api'
import { ElMessage, ElImageViewer, type FormInstance, type FormRules, type UploadFile } from 'element-plus'

interface UploadedImage {
  imagePath: string
  originalName: string
  ocrAmount: number
  sortOrder: number
}

const router = useRouter()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const realName = ref(userStore.user?.alipayAccount || '')
const fileList = ref<UploadFile[]>([])
const uploadedImages = ref<UploadedImage[]>([])

const previewVisible = ref(false)
const previewUrl = ref('')

const uploadUrl = '/api/reimbursement/upload'
const uploadHeaders = computed(() => ({
  Authorization: `Bearer ${userStore.token}`
}))

const form = reactive({
  vpnStartDate: null as Date | null,
  vpnEndDate: null as Date | null,
  totalAmount: 0
})

const rules: FormRules = {
  vpnStartDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
  vpnEndDate: [{ required: true, message: '请选择结束日期', trigger: 'change' }]
}

const canSubmit = computed(() => {
  return realName.value.trim() &&
    form.vpnStartDate &&
    form.vpnEndDate &&
    form.totalAmount > 0 &&
    uploadedImages.value.length > 0
})

function handlePreview(file: UploadFile) {
  previewUrl.value = file.url || ''
  previewVisible.value = true
}

function beforeUpload(file: File) {
  const isImage = file.type.startsWith('image/')
  const isLt10M = file.size / 1024 / 1024 < 10

  if (!isImage) {
    ElMessage.error('只能上传图片文件')
    return false
  }
  if (!isLt10M) {
    ElMessage.error('图片大小不能超过10MB')
    return false
  }
  return true
}

function handleUploadSuccess(response: any) {
  if (response.code === 200) {
    uploadedImages.value.push({
      imagePath: response.data.imagePath,
      originalName: response.data.originalName,
      ocrAmount: response.data.ocrAmount || 0,
      sortOrder: uploadedImages.value.length
    })
    calculateTotal()
    if (!response.data.ocrAmount) {
      ElMessage.warning('图片金额未识别，请手动输入')
    } else {
      ElMessage.success('上传成功')
    }
  } else {
    ElMessage.error(response.message || '上传失败')
  }
}

function handleUploadError() {
  ElMessage.error('上传失败')
}

function handleRemove(file: UploadFile) {
  const imagePath = file.response?.data?.imagePath
  if (imagePath) {
    const index = uploadedImages.value.findIndex(img => img.imagePath === imagePath)
    if (index !== -1) {
      uploadedImages.value.splice(index, 1)
      calculateTotal()
    }
  }
}

function calculateTotal() {
  form.totalAmount = uploadedImages.value.reduce((sum, img) => sum + (img.ocrAmount || 0), 0)
}

async function handleSubmit() {
  if (!formRef.value) return
  if (!realName.value.trim()) {
    ElMessage.warning('请填写真实姓名')
    return
  }

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitting.value = true
    try {
      // 如果真实姓名有变更，先保存
      if (realName.value.trim() !== userStore.user?.alipayAccount) {
        await updateAlipayAccount(realName.value.trim())
        userStore.updateAlipayAccount(realName.value.trim())
      }
      await createReimbursement({
        totalAmount: form.totalAmount,
        vpnStartDate: form.vpnStartDate,
        vpnEndDate: form.vpnEndDate,
        images: uploadedImages.value
      })
      ElMessage.success('报销申请提交成功')
      router.push('/reimbursement')
    } catch (error: any) {
      ElMessage.error(error.message || '提交失败')
    } finally {
      submitting.value = false
    }
  })
}
</script>

<style scoped>
.apply {
  max-width: 800px;
}

.page-title {
  margin-bottom: 24px;
  font-size: 20px;
  color: #303133;
}

.apply-card {
  padding: 20px;
}

.amount-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.amount-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.no-images {
  color: #909399;
  font-size: 14px;
}

.amount-tip {
  color: #E6A23C;
  font-size: 12px;
  margin-left: 8px;
}

.form-tip {
  color: #E6A23C;
  font-size: 12px;
  margin-top: 4px;
  width: 100%;
}
</style>
