<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { authApi } from '../api/auth'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const loginFormRef = ref<FormInstance>()
const loginForm = reactive({
  username: '',
  password: '',
})
const loading = ref(false)

const rules = reactive<FormRules>({
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
})

const handleLogin = async () => {
  if (!loginFormRef.value) return
  await loginFormRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const { token, user } = await authApi.login({
        username: loginForm.username.trim(),
        password: loginForm.password,
      })
      authStore.setAuth(token, user)
      ElMessage.success('登录成功')
      const redirect = (route.query.redirect as string) || '/vehicles'
      router.replace(redirect)
    } catch (e: any) {
      // 错误已在拦截器提示
    } finally {
      loading.value = false
    }
  })
}
</script>

<template>
  <div class="login-page">
    <el-card class="login-card">
      <template #header>
        <div class="card-header">
          <span>车辆遥测平台</span>
          <span class="subtitle">登录</span>
        </div>
      </template>

      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="rules"
        label-width="0"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="用户名 (admin / viewer)"
            :prefix-icon="User"
            size="large"
            clearable
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="密码 (admin123 / viewer123)"
            :prefix-icon="Lock"
            size="large"
            show-password
            clearable
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            style="width: 100%"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="tips">
        <p>测试账号：</p>
        <p>ADMIN: admin / admin123 （可维护车辆）</p>
        <p>VIEWER: viewer / viewer123 （仅查看）</p>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e7ed 100%);
}
.login-card {
  width: 420px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}
.card-header {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  text-align: center;
}
.card-header .subtitle {
  margin-left: 8px;
  font-size: 14px;
  color: #909399;
  font-weight: 400;
}
.tips {
  margin-top: 16px;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
  text-align: center;
}
.tips p {
  margin: 2px 0;
}
</style>
