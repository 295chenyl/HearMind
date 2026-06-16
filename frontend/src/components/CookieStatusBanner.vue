<template>
  <div v-if="visible" class="cookie-banner" :class="bannerClass">
    <div class="cookie-banner-body">
      <span class="cookie-banner-tag">B 站 Cookie</span>
      <p class="cookie-banner-text">{{ health.cookieMessage }}</p>
    </div>
    <button
      v-if="dismissible"
      type="button"
      class="cookie-banner-close"
      aria-label="关闭提示"
      @click="dismissed = true"
    >
      ×
    </button>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { getImportHealth } from '../api/system'
import { useAuth } from '../composables/useAuth'

const props = defineProps({
  refreshKey: { type: Number, default: 0 }
})

const { loggedIn } = useAuth()

const health = ref(null)
const dismissed = ref(false)

const needsAttention = computed(() => {
  if (!health.value) return false
  const status = health.value.cookieStatus
  return status === 'MISSING'
    || status === 'INVALID'
    || status === 'WARN'
    || status === 'EXPIRED'
})

const visible = computed(() => loggedIn.value && needsAttention.value && !dismissed.value)

const bannerClass = computed(() => {
  const status = health.value?.cookieStatus
  if (status === 'EXPIRED') return 'is-expired'
  if (status === 'WARN') return 'is-warn'
  if (status === 'INVALID') return 'is-invalid'
  return 'is-missing'
})

const dismissible = computed(() => health.value?.cookieStatus === 'WARN')

async function loadHealth() {
  if (!loggedIn.value) {
    health.value = null
    return
  }
  try {
    const { data } = await getImportHealth()
    health.value = data
    if (data.cookieStatus === 'EXPIRED' || data.cookieStatus === 'MISSING' || data.cookieStatus === 'INVALID') {
      dismissed.value = false
    }
  } catch {
    health.value = null
  }
}

onMounted(loadHealth)
watch(() => loggedIn.value, loadHealth)
watch(() => props.refreshKey, loadHealth)
</script>

<style scoped>
.cookie-banner {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 20px;
  border-bottom: 1px solid transparent;
  background: rgba(20, 14, 32, 0.98);
}

.cookie-banner.is-missing,
.cookie-banner.is-invalid {
  border-bottom-color: rgba(255, 145, 0, 0.45);
  background: rgba(255, 145, 0, 0.08);
}

.cookie-banner.is-warn {
  border-bottom-color: rgba(255, 204, 128, 0.5);
  background: rgba(255, 193, 7, 0.08);
}

.cookie-banner.is-expired {
  border-bottom-color: rgba(213, 0, 0, 0.55);
  background: rgba(213, 0, 0, 0.12);
}

.cookie-banner-body {
  flex: 1;
  min-width: 0;
}

.cookie-banner-tag {
  display: inline-block;
  margin-right: 10px;
  font-size: 10px;
  letter-spacing: 0.15em;
  color: var(--eva-orange);
  font-family: Consolas, monospace;
}

.cookie-banner-text {
  display: inline;
  margin: 0;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.85);
  line-height: 1.5;
}

.cookie-banner-close {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  color: rgba(255, 255, 255, 0.55);
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
  margin-left: auto;
}

.cookie-banner-close:hover {
  color: #fff;
}
</style>
