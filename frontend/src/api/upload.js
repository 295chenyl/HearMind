import api from './client'

const CHUNK_SIZE = 5 * 1024 * 1024

export { CHUNK_SIZE }

async function computeFileSha256(file) {
  const buffer = await file.arrayBuffer()
  const hash = await crypto.subtle.digest('SHA-256', buffer)
  return Array.from(new Uint8Array(hash)).map((b) => b.toString(16).padStart(2, '0')).join('')
}

export function initUpload(payload) {
  return api.post('/videos/upload/init', payload)
}

export function listUploadSessions() {
  return api.get('/videos/upload/sessions')
}

export function getUploadSession(sessionId) {
  return api.get(`/videos/upload/${sessionId}`)
}

export function uploadChunk(sessionId, chunkIndex, blob, onProgress) {
  const formData = new FormData()
  formData.append('file', blob, `chunk-${chunkIndex}`)
  return api.put(`/videos/upload/${sessionId}/chunks/${chunkIndex}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: onProgress
  })
}

export function completeUpload(sessionId) {
  return api.post(`/videos/upload/${sessionId}/complete`)
}

export async function resumableUpload(file, { onProgress, contentHash } = {}) {
  // 大文件跳过客户端全量 SHA-256，避免占满内存；服务端 complete 时仍会计算哈希
  const hash = contentHash
    ?? (file.size <= 200 * 1024 * 1024 ? await computeFileSha256(file) : null)
  const { data: initData } = await initUpload({
    filename: file.name,
    fileSize: file.size,
    chunkSize: CHUNK_SIZE,
    contentHash: hash
  })

  const sessionId = initData.sessionId
  const totalChunks = initData.totalChunks
  const uploaded = new Set(initData.uploadedChunks || [])

  for (let i = 0; i < totalChunks; i++) {
    if (uploaded.has(i)) continue
    const start = i * CHUNK_SIZE
    const end = Math.min(file.size, start + CHUNK_SIZE)
    const blob = file.slice(start, end)
    await uploadChunk(sessionId, i, blob, (e) => {
      if (onProgress) {
        const chunkProgress = e.loaded / (e.total || blob.size)
        const overall = (i + chunkProgress) / totalChunks
        onProgress(Math.min(99, Math.round(overall * 100)))
      }
    })
  }

  const { data: video } = await completeUpload(sessionId)
  if (onProgress) onProgress(100)
  return { video }
}
