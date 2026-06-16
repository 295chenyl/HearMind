const TOKEN_KEY = 'hearmind_token'
const USER_KEY = 'hearmind_user'
const AUTH_CHANGED = 'hearmind-auth-changed'

function emitAuthChanged() {
  window.dispatchEvent(new Event(AUTH_CHANGED))
}

export function onAuthChanged(listener) {
  window.addEventListener(AUTH_CHANGED, listener)
  return () => window.removeEventListener(AUTH_CHANGED, listener)
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setAuth(token, user) {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
  emitAuthChanged()
}

export function getUser() {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  emitAuthChanged()
}

export function isLoggedIn() {
  return !!getToken()
}
