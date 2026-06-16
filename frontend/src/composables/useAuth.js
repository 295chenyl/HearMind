import { ref, computed, onMounted, onUnmounted } from 'vue'
import { getToken, getUser, setAuth as persistAuth, clearAuth as wipeAuth, onAuthChanged } from '../utils/auth'
import { fetchMe } from '../api/auth'

const authTick = ref(0)

export function useAuth() {
  const loggedIn = computed(() => {
    authTick.value
    return !!getToken()
  })

  const user = computed(() => {
    authTick.value
    return getUser()
  })

  const userLabel = computed(() => {
    const u = user.value
    return u?.displayName || u?.username || '用户'
  })

  function bump() {
    authTick.value++
  }

  function setAuth(token, userData) {
    persistAuth(token, userData)
  }

  function clearAuth() {
    wipeAuth()
  }

  async function syncFromServer() {
    if (!getToken()) return
    try {
      const { data } = await fetchMe()
      persistAuth(getToken(), data)
    } catch {
      /* 401 由 client 拦截器处理 */
    }
  }

  return { loggedIn, user, userLabel, setAuth, clearAuth, syncFromServer, bump }
}

export function useAuthListener() {
  const { bump } = useAuth()
  onMounted(() => {
    const off = onAuthChanged(bump)
    onUnmounted(off)
  })
}
