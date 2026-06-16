<template>

  <el-container class="layout">

    <el-header v-if="!isLoginPage" class="header">

      <div class="brand" @click="$router.push('/')">

        <span class="brand-name">听悟</span>

        <span class="brand-en">HearMind</span>

      </div>

      <el-menu

        v-if="loggedIn"

        mode="horizontal"

        :ellipsis="false"

        router

        :default-active="$route.path"

        class="nav-menu"

      >

        <el-menu-item index="/">上传</el-menu-item>

        <el-menu-item index="/videos">视频列表</el-menu-item>

      </el-menu>

      <div class="header-actions">

        <template v-if="loggedIn">

          <span class="user-name">{{ userLabel }}</span>

          <button type="button" class="logout-btn" @click="logout">退出</button>

        </template>

        <router-link v-else to="/login" class="login-link">登录</router-link>

      </div>

    </el-header>

    <CookieStatusBanner v-if="!isLoginPage && loggedIn" :refresh-key="cookieRefreshKey" />

    <el-main class="main">

      <router-view :key="routeKey" />

    </el-main>

  </el-container>

</template>



<script setup>

import { computed, onMounted, ref } from 'vue'

import { useRoute, useRouter } from 'vue-router'

import { useAuth, useAuthListener } from './composables/useAuth'

import CookieStatusBanner from './components/CookieStatusBanner.vue'



const route = useRoute()

const router = useRouter()

useAuthListener()

const { loggedIn, userLabel, clearAuth, syncFromServer } = useAuth()

const cookieRefreshKey = ref(0)

onMounted(() => {
  syncFromServer()
})



const isLoginPage = computed(() => route.path === '/login')

const routeKey = computed(() => `${route.path}-${loggedIn.value ? userLabel.value : 'guest'}`)



function logout() {

  clearAuth()

  router.push('/login')

}

</script>



<style scoped>

.layout {

  min-height: 100vh;

  background: var(--eva-bg);

}

.header {

  display: flex;

  align-items: center;

  gap: 24px;

  border-bottom: 1px solid rgba(118, 255, 3, 0.15);

  background: rgba(10, 8, 16, 0.95);

}

.brand {

  cursor: pointer;

  white-space: nowrap;

  display: flex;

  align-items: center;

  gap: 8px;

}

.brand-name {

  font-size: 18px;

  font-weight: 700;

  color: #f5f5f7;

  letter-spacing: 0.04em;

}

.brand-en {

  font-size: 10px;

  padding: 2px 8px;

  border: 1px solid rgba(118, 255, 3, 0.45);

  color: var(--eva-green);

  font-family: Consolas, monospace;

  letter-spacing: 0.12em;

  text-transform: uppercase;

}

.nav-menu {

  background: transparent;

  border: none;

  flex: 1;

}

.nav-menu :deep(.el-menu-item) {

  color: rgba(255, 255, 255, 0.65);

  border-bottom: 2px solid transparent;

}

.nav-menu :deep(.el-menu-item:hover),

.nav-menu :deep(.el-menu-item.is-active) {

  color: var(--eva-green) !important;

  background: transparent !important;

  border-bottom-color: var(--eva-green);

}

.header-actions {

  display: flex;

  align-items: center;

  gap: 12px;

  margin-left: auto;

}

.user-name {

  font-size: 13px;

  color: rgba(255, 255, 255, 0.65);

}

.logout-btn {

  background: transparent;

  border: 1px solid rgba(255, 255, 255, 0.25);

  color: rgba(255, 255, 255, 0.65);

  padding: 4px 12px;

  font-size: 12px;

  cursor: pointer;

  border-radius: 2px;

}

.logout-btn:hover {

  border-color: var(--eva-orange);

  color: var(--eva-orange);

}

.login-link {

  color: var(--eva-green);

  font-size: 13px;

  text-decoration: none;

}

.main {

  background: var(--eva-bg);

  padding: 0;

}

</style>



<style>

:root {

  --eva-bg: #0a0810;

  --eva-purple: #6a1b9a;

  --eva-purple-dark: #4a148c;

  --eva-green: #76ff03;

  --eva-orange: #ff9100;

  --eva-red: #d50000;

}



body {

  margin: 0;

  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;

  background: var(--eva-bg);

  color: #f5f5f7;

}



.el-main {

  --el-bg-color: var(--eva-bg);

}

</style>


