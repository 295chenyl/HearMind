import api from './client'
import { clearAuth, getToken } from '../utils/auth'

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

export async function chatStream(videoId, message, sessionId, onEvent, { signal } = {}) {
  const token = getToken()
  const response = await fetch(`/api/videos/${videoId}/chat/stream`, {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify({ message, sessionId }),
    signal
  })

  if (!response.ok) {
    await throwStreamRequestError(response)
  }
  if (!response.body) {
    throw new Error('浏览器不支持流式响应')
  }

  await consumeEventStream(response.body, onEvent)
}

async function throwStreamRequestError(response) {
  let message = `请求失败（${response.status}）`
  try {
    const data = await response.json()
    if (data?.message) message = data.message
  } catch {
    // Keep the status-based fallback when the response is not JSON.
  }

  if (response.status === 401) {
    clearAuth()
    if (!window.location.pathname.startsWith('/login')) {
      window.location.href = '/login'
    }
  }
  throw new Error(message)
}

async function consumeEventStream(stream, onEvent) {
  const reader = stream.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done })
    buffer = buffer.replace(/\r\n/g, '\n')

    let boundary = buffer.indexOf('\n\n')
    while (boundary >= 0) {
      await dispatchEventBlock(buffer.slice(0, boundary), onEvent)
      buffer = buffer.slice(boundary + 2)
      boundary = buffer.indexOf('\n\n')
    }
    if (done) break
  }

  if (buffer.trim()) {
    await dispatchEventBlock(buffer, onEvent)
  }
}

async function dispatchEventBlock(block, onEvent) {
  let eventName = 'message'
  const dataLines = []

  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart())
    }
  }

  if (!dataLines.length) return
  const data = JSON.parse(dataLines.join('\n'))
  if (eventName === 'error') {
    throw new Error(data.message || '问答生成失败')
  }
  await onEvent?.(eventName, data)
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
