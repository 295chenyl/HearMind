<template>
  <div class="auth-page">
    <div class="auth-card">
      <header class="auth-header">
        <h1>听悟 <span class="brand-en">HearMind</span></h1>
        <p class="tagline">听懂每一帧，悟透每一个问题</p>
        <p>{{ mode === 'login' ? '登录你的账号' : '创建新账号' }}</p>
        <p v-if="loggedIn" class="switch-hint">当前已登录，提交后将切换到新账号</p>
      </header>

      <div class="mode-switch">
        <button type="button" :class="{ active: mode === 'login' }" @click="mode = 'login'">登录</button>
        <button type="button" :class="{ active: mode === 'register' }" @click="mode = 'register'">注册</button>
      </div>

      <form class="auth-form" @submit.prevent="submit">
        <label>
          <span>用户名</span>
          <input v-model="username" type="text" autocomplete="username" placeholder="3-32 个字符" />
        </label>
        <label v-if="mode === 'register'">
          <span>昵称（可选）</span>
          <input v-model="displayName" type="text" placeholder="显示名称" />
        </label>
        <label>
          <span>密码</span>
          <input
            v-model="password"
            type="password"
            autocomplete="current-password"
            placeholder="至少 6 位"
          />
        </label>
        <button type="submit" class="submit-btn" :disabled="loading">
          {{ loading ? '处理中…' : mode === 'login' ? '登录' : '注册并登录' }}
        </button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, register } from '../api/auth'
import { useAuth } from '../composables/useAuth'

const router = useRouter()
const { setAuth, loggedIn } = useAuth()
const mode = ref('login')
const username = ref('')
const password = ref('')
const displayName = ref('')
const loading = ref(false)

async function submit() {
  if (!username.value.trim() || !password.value) {
    ElMessage.warning('请填写用户名和密码')
    return
  }
  loading.value = true
  try {
    const { data } = mode.value === 'login'
      ? await login(username.value.trim(), password.value)
      : await register(username.value.trim(), password.value, displayName.value.trim() || undefined)
    setAuth(data.token, data.user)
    ElMessage.success(mode.value === 'login' ? '登录成功' : '注册成功')
    router.push('/')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.auth-card {
  width: 100%;
  max-width: 400px;
  padding: 32px 28px;
  background: rgba(14, 10, 22, 0.92);
  border: 1px solid rgba(106, 27, 154, 0.55);
  border-radius: 4px;
  box-shadow: 0 0 40px rgba(106, 27, 154, 0.15);
}

.auth-header h1 {
  margin: 0 0 8px;
  font-size: 24px;
  color: #f5f5f7;
  letter-spacing: 0.04em;
}

.brand-en {
  font-size: 11px;
  padding: 2px 8px;
  border: 1px solid rgba(118, 255, 3, 0.45);
  color: var(--eva-green);
  font-family: Consolas, monospace;
  vertical-align: middle;
  margin-left: 6px;
}

.tagline {
  margin: 0 0 8px !important;
  color: rgba(118, 255, 3, 0.75) !important;
  font-size: 13px !important;
  letter-spacing: 0.06em;
}

.auth-header p {
  margin: 0 0 24px;
  color: rgba(255, 255, 255, 0.5);
  font-size: 14px;
}

.switch-hint {
  margin: -16px 0 20px !important;
  color: var(--eva-orange) !important;
  font-size: 12px !important;
}

.mode-switch {
  display: flex;
  gap: 0;
  margin-bottom: 24px;
  border: 1px solid rgba(118, 255, 3, 0.25);
  border-radius: 2px;
  overflow: hidden;
}

.mode-switch button {
  flex: 1;
  padding: 10px;
  background: transparent;
  border: none;
  color: rgba(255, 255, 255, 0.5);
  cursor: pointer;
  font-size: 14px;
}

.mode-switch button.active {
  background: rgba(118, 255, 3, 0.12);
  color: var(--eva-green);
}

.auth-form label {
  display: block;
  margin-bottom: 16px;
}

.auth-form label span {
  display: block;
  margin-bottom: 6px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.55);
}

.auth-form input {
  width: 100%;
  box-sizing: border-box;
  padding: 12px;
  background: rgba(0, 0, 0, 0.35);
  border: 1px solid rgba(118, 255, 3, 0.3);
  border-radius: 4px;
  color: #fff;
  font-size: 14px;
}

.auth-form input:focus {
  outline: none;
  border-color: var(--eva-green);
}

.submit-btn {
  width: 100%;
  margin-top: 8px;
  padding: 14px;
  background: rgba(118, 255, 3, 0.12);
  border: 1px solid var(--eva-green);
  color: var(--eva-green);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  border-radius: 2px;
}

.submit-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
