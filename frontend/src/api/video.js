import api from './client'
import { getToken } from '../utils/auth'

export function uploadVideo(file) {
  const formData = new FormData()
  formData.append('file', file)
  return api.post('/videos/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function previewImportUrl(url) {
  const formData = new FormData()
  formData.append('url', url)
  return api.post('/videos/import-url/preview', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function importUrl(url, { contentType = 'GENERAL' } = {}) {
  const formData = new FormData()
  formData.append('url', url)
  formData.append('contentType', contentType)
  return api.post('/videos/import-url', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function listVideos() {
  return api.get('/videos')
}

export function getVideo(id) {
  return api.get(`/videos/${id}`)
}

export function getTranscript(id) {
  return api.get(`/videos/${id}/transcript`)
}

export function getSummary(id) {
  return api.get(`/videos/${id}/summary`)
}

export function updateSummary(id, content) {
  return api.put(`/videos/${id}/summary`, { content })
}

export function regenerateSummary(id) {
  return api.post(`/videos/${id}/summary/regenerate`)
}

export function updateContentType(id, contentType) {
  return api.put(`/videos/${id}/content-type`, null, { params: { contentType } })
}

export function rebuildIndex(id) {
  return api.post(`/videos/${id}/index/rebuild`)
}

export function exportMarkdown(id) {
  const token = getToken()
  const base = `/api/videos/${id}/export/markdown`
  return token ? `${base}?token=${encodeURIComponent(token)}` : base
}

export function chat(videoId, message, sessionId) {
  return api.post(`/videos/${videoId}/chat`, { message, sessionId })
}

export function getChatHistory(videoId) {
  return api.get(`/videos/${videoId}/chat/history`)
}

export function getVideoStreamUrl(id) {
  const token = getToken()
  const base = `/api/videos/${id}/stream`
  return token ? `${base}?token=${encodeURIComponent(token)}` : base
}

export function retryVideo(id) {
  return api.post(`/videos/${id}/retry`)
}

export default api
