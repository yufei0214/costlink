<template>
  <el-container class="layout-container">
    <el-aside width="220px" class="layout-aside">
      <div class="logo">
        <h2>CostLink</h2>
      </div>

      <el-menu
        :default-active="route.path"
        router
        class="layout-menu"
      >
        <el-menu-item v-if="userStore.isAdmin" index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>仪表盘</span>
        </el-menu-item>

        <el-menu-item index="/apply">
          <el-icon><Plus /></el-icon>
          <span>报销申请</span>
        </el-menu-item>

        <el-menu-item index="/reimbursement">
          <el-icon><Document /></el-icon>
          <span>我的报销</span>
        </el-menu-item>

        <el-menu-item v-if="userStore.isAdmin" index="/management">
          <el-icon><Setting /></el-icon>
          <span>报销管理</span>
        </el-menu-item>

        <el-menu-item index="/profile">
          <el-icon><User /></el-icon>
          <span>个人中心</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="layout-header">
        <div class="header-content">
          <span class="welcome">欢迎，{{ userStore.user?.displayName || userStore.user?.username }}</span>
          <el-button type="text" @click="handleLogout">
            <el-icon><SwitchButton /></el-icon>
            退出登录
          </el-button>
        </div>
      </el-header>

      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

onMounted(async () => {
  if (userStore.isLoggedIn && !userStore.user) {
    await userStore.fetchUser()
  }
})

async function handleLogout() {
  await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout-container {
  min-height: 100vh;
}

.layout-aside {
  background-color: #304156;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #263445;
}

.logo h2 {
  color: #fff;
  font-size: 20px;
}

.layout-menu {
  border-right: none;
  background-color: #304156;
}

.layout-menu .el-menu-item {
  color: #bfcbd9;
}

.layout-menu .el-menu-item:hover {
  background-color: #263445;
}

.layout-menu .el-menu-item.is-active {
  color: #409eff;
  background-color: #263445;
}

.layout-header {
  background-color: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

.header-content {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.welcome {
  font-size: 14px;
  color: #606266;
}

.layout-main {
  background-color: #f5f7fa;
  padding: 20px;
}
</style>
