<template>
  <div class="profile">
    <h2 class="page-title">个人中心</h2>

    <el-card class="profile-card">
      <el-form :model="form" label-width="120px">
        <el-form-item label="用户名">
          <el-input :value="userStore.user?.username" disabled />
        </el-form-item>

        <el-form-item label="显示名称">
          <el-input :value="userStore.user?.displayName" disabled />
        </el-form-item>

        <el-form-item label="角色">
          <el-tag :type="userStore.isAdmin ? 'danger' : 'info'">
            {{ userStore.isAdmin ? '管理员' : '普通用户' }}
          </el-tag>
        </el-form-item>

        <el-divider />

        <el-form-item label="真实姓名">
          <el-input
            v-model="form.alipayAccount"
            placeholder="请输入您的真实姓名（用于转账）"
          />
        </el-form-item>

        <el-form-item label="所属组">
          <el-select
            v-model="form.department"
            placeholder="请选择所属组"
            style="width: 100%"
          >
            <el-option
              v-for="opt in DEPARTMENT_OPTIONS"
              :key="opt"
              :label="opt"
              :value="opt"
            />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="saving" @click="saveProfile">
            保存
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { updateAlipayAccount, updateDepartment } from '@/api'
import { ElMessage } from 'element-plus'

const DEPARTMENT_OPTIONS = [
  '研发1组-数据组',
  '研发1组-应用组',
  '研发1组-SDK组',
  '研发1组-质量组',
  '研发2组'
]

const userStore = useUserStore()
const saving = ref(false)

const form = reactive({
  alipayAccount: '',
  department: ''
})

onMounted(() => {
  form.alipayAccount = userStore.user?.alipayAccount || ''
  form.department = userStore.user?.department || ''
})

async function saveProfile() {
  if (!form.alipayAccount.trim()) {
    ElMessage.warning('请输入真实姓名')
    return
  }
  if (!form.department) {
    ElMessage.warning('请选择所属组')
    return
  }

  saving.value = true
  try {
    if (form.alipayAccount.trim() !== (userStore.user?.alipayAccount || '')) {
      await updateAlipayAccount(form.alipayAccount.trim())
      userStore.updateAlipayAccount(form.alipayAccount.trim())
    }
    if (form.department !== (userStore.user?.department || '')) {
      await updateDepartment(form.department)
      userStore.updateDepartment(form.department)
    }
    ElMessage.success('保存成功')
  } catch (error: any) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.profile {
  max-width: 600px;
}

.page-title {
  margin-bottom: 24px;
  font-size: 20px;
  color: #303133;
}

.profile-card {
  padding: 20px;
}
</style>
