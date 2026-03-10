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

        <el-form-item>
          <el-button type="primary" :loading="saving" @click="saveAlipayAccount">
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
import { updateAlipayAccount } from '@/api'
import { ElMessage } from 'element-plus'

const userStore = useUserStore()
const saving = ref(false)

const form = reactive({
  alipayAccount: ''
})

onMounted(() => {
  form.alipayAccount = userStore.user?.alipayAccount || ''
})

async function saveAlipayAccount() {
  if (!form.alipayAccount.trim()) {
    ElMessage.warning('请输入真实姓名')
    return
  }

  saving.value = true
  try {
    await updateAlipayAccount(form.alipayAccount)
    userStore.updateAlipayAccount(form.alipayAccount)
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
