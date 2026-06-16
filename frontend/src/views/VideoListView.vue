<template>
  <div class="list-page" v-loading="loading">
    <header class="list-header">
      <div>
        <p class="list-tag">HEARMIND · LIBRARY</p>
        <h1>我的视频</h1>
        <p class="list-desc">已导入的音视频将在此展示，处理完成后可转写、摘要与深问</p>
      </div>
      <button type="button" class="upload-btn" @click="$router.push('/')">上传新视频</button>
    </header>

    <div v-if="!loading && !videos.length" class="empty-state">
      <p>还没有视频</p>
      <button type="button" class="upload-btn" @click="$router.push('/')">去上传</button>
    </div>

    <div v-else class="video-grid">
      <article
        v-for="item in videos"
        :key="item.id"
        class="video-card"
        @click="$router.push(`/videos/${item.id}`)"
      >
        <div class="card-top">
          <span class="source-badge">{{ item.sourceType === 'UPLOAD' ? '本地上传' : '链接导入' }}</span>
          <span class="status-badge" :class="item.status.toLowerCase()">{{ statusText(item.status) }}</span>
        </div>
        <h2 class="card-title" :title="item.title">{{ item.title }}</h2>
        <div class="card-meta">
          <span v-if="item.platform">{{ item.platform }}</span>
          <span v-if="item.durationSec">{{ formatDuration(item.durationSec) }}</span>
          <span>{{ formatTime(item.createdAt) }}</span>
        </div>
      </article>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listVideos } from '../api/video'

const videos = ref([])
const loading = ref(false)

const statusMap = {
  PENDING: '等待中',
  DOWNLOADING: '下载中',
  TRANSCRIBING: '转写中',
  SUMMARIZING: '生成摘要',
  READY: '已完成',
  FAILED: '失败'
}

function statusText(status) {
  return statusMap[status] || status
}

function formatTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function formatDuration(sec) {
  if (!sec) return ''
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

async function load() {
  loading.value = true
  try {
    const { data } = await listVideos()
    videos.value = data
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.list-page {
  max-width: 1100px;
  margin: 0 auto;
  padding: 24px 20px 48px;
  min-height: calc(100vh - 60px);
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 28px;
  gap: 16px;
  flex-wrap: wrap;
}

.list-tag {
  margin: 0 0 4px;
  font-size: 10px;
  letter-spacing: 0.25em;
  color: var(--eva-green);
  font-family: Consolas, monospace;
}

.list-header h1 {
  margin: 0 0 6px;
  font-size: 24px;
  color: #f5f5f7;
}

.list-desc {
  margin: 0;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.4);
  line-height: 1.5;
}

.upload-btn {
  padding: 10px 20px;
  background: rgba(118, 255, 3, 0.12);
  border: 1px solid var(--eva-green);
  color: var(--eva-green);
  font-size: 13px;
  cursor: pointer;
  border-radius: 2px;
  white-space: nowrap;
}

.upload-btn:hover {
  background: rgba(118, 255, 3, 0.2);
}

.empty-state {
  text-align: center;
  padding: 80px 24px;
  color: rgba(255, 255, 255, 0.45);
}

.empty-state p {
  margin: 0 0 20px;
  font-size: 16px;
}

.video-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.video-card {
  padding: 18px;
  background: rgba(14, 10, 22, 0.92);
  border: 1px solid rgba(106, 27, 154, 0.5);
  border-radius: 4px;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.video-card:hover {
  border-color: var(--eva-green);
  box-shadow: 0 0 20px rgba(118, 255, 3, 0.1);
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  gap: 8px;
}

.source-badge {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.45);
}

.status-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 2px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  color: rgba(255, 255, 255, 0.65);
}

.status-badge.ready {
  border-color: rgba(118, 255, 3, 0.5);
  color: var(--eva-green);
}

.status-badge.failed {
  border-color: rgba(213, 0, 0, 0.5);
  color: #ff8a80;
}

.status-badge.pending,
.status-badge.downloading,
.status-badge.transcribing,
.status-badge.summarizing {
  border-color: rgba(255, 145, 0, 0.5);
  color: var(--eva-orange);
}

.card-title {
  margin: 0 0 12px;
  font-size: 16px;
  font-weight: 600;
  color: #f5f5f7;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.4);
}
</style>
