import api from './client'

export function login(username, password) {
  return api.post('/auth/login', { username, password })
}

export function register(username, password, displayName) {
  return api.post('/auth/register', { username, password, displayName })
}

export function fetchMe() {
  return api.get('/auth/me')
}
