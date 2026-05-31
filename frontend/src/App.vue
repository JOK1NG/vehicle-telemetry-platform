<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
import { useAuthStore } from './stores/auth'
import { useRouter } from 'vue-router'

const authStore = useAuthStore()
const router = useRouter()

const handleLogout = () => {
  authStore.logout()
  router.replace('/login')
}
</script>

<template>
  <el-container class="app-layout">
    <el-header class="app-header">
      <div class="header-left">
        <span class="logo">车辆遥测平台</span>
      </div>
      <el-menu mode="horizontal" :ellipsis="false" class="header-menu">
        <el-menu-item index="1">
          <RouterLink to="/dashboard">监控大屏</RouterLink>
        </el-menu-item>
        <el-menu-item index="2">
          <RouterLink to="/vehicles">车辆列表</RouterLink>
        </el-menu-item>
        <el-menu-item v-if="!authStore.isLoggedIn" index="3">
          <RouterLink to="/login">登录</RouterLink>
        </el-menu-item>
      </el-menu>
      <div class="header-right">
        <div v-if="authStore.isLoggedIn" class="user-box">
          <span class="user-name">{{ authStore.username }}（{{ authStore.role }}）</span>
          <el-button size="small" type="danger" link @click="handleLogout">退出登录</el-button>
        </div>
        <span v-else class="env-tag">M1 登录态 + 车辆列表</span>
      </div>
    </el-header>
    <el-main class="app-main">
      <RouterView />
    </el-main>
  </el-container>
</template>

<style scoped>
.app-layout {
  min-height: 100vh;
}
.app-header {
  background: #fff;
  border-bottom: 1px solid #e5e4e7;
  display: flex;
  align-items: center;
  padding: 0 24px;
}
.header-left {
  display: flex;
  align-items: center;
  margin-right: 32px;
}
.logo {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.header-menu {
  flex: 1;
  border-bottom: none;
}
.header-right {
  display: flex;
  align-items: center;
}
  .env-tag {
    font-size: 12px;
    padding: 2px 8px;
    background: #f0f0f0;
    border-radius: 4px;
    color: #909399;
  }
  .user-box {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
  }
  .user-name {
    color: #606266;
  }
.app-main {
  padding: 24px;
  background: #f5f7fa;
}
</style>
