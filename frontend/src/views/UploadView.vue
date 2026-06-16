<template>
  <div class="eva-upload">
    <div class="eva-bg-grid" aria-hidden="true" />

    <header class="eva-header">
      <p class="eva-tag">HEARMIND · INGEST</p>
      <h1 class="eva-title">导入音视频</h1>
      <p class="eva-subtitle">听懂每一帧，悟透每一个问题</p>
      <p class="eva-desc">本地上传或粘贴链接，系统将自动完成转写、摘要与知识增强问答</p>
    </header>

    <div class="portal-row">
      <button
        type="button"
        class="portal-card"
        :class="{ active: activeMode === 'local' }"
        @click="toggleMode('local')"
      >
        <span class="portal-index">01</span>
        <span class="portal-icon">
          <el-icon :size="36"><UploadFilled /></el-icon>
        </span>
        <span class="portal-label">LOCAL UPLOAD</span>
        <span class="portal-name">本地上传</span>
        <span class="portal-desc">拖拽或选择本地视频文件</span>
      </button>

      <div class="portal-divider" aria-hidden="true">
        <span>OR</span>
      </div>

      <button
        type="button"
        class="portal-card"
        :class="{ active: activeMode === 'weblink' }"
        @click="toggleMode('weblink')"
      >
        <span class="portal-index">02</span>
        <span class="portal-icon">
          <el-icon :size="36"><Link /></el-icon>
        </span>
        <span class="portal-label">WEB LINK</span>
        <span class="portal-name">链接导入</span>
        <span class="portal-desc">B 站 / YouTube / 直链 URL</span>
      </button>
    </div>

    <Transition name="panel-slide">
      <section v-if="activeMode === 'local'" class="ingest-panel">
        <div class="panel-head">
          <span class="panel-tag">LOCAL · INGEST</span>
          <button type="button" class="panel-close" @click="activeMode = null">收起</button>
        </div>
        <el-upload
          class="eva-uploader"
          drag
          :auto-upload="false"
          :limit="1"
          :on-change="handleFileChange"
          :on-remove="() => (selectedFile = null)"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">拖拽视频到此处，或 <em>点击选择</em></div>
          <template #tip>
            <div class="el-upload__tip">常见视频格式 · 时长 ≤ 50 分钟</div>
          </template>
        </el-upload>
        <div v-if="selectedFile" class="file-chip">
          <span>{{ selectedFile.name }}</span>
        </div>
        <div v-if="uploadProgress > 0 && uploading" class="progress-wrap">
          <div class="progress-bar">
            <div class="progress-fill" :style="{ width: uploadProgress + '%' }" />
          </div>
          <span class="progress-text">上传进度 {{ uploadProgress }}%（大文件自动分片续传）</span>
        </div>
        <button
          type="button"
          class="eva-submit"
          :disabled="!selectedFile || uploading"
          @click="submitUpload"
        >
          {{ uploading ? '上传中…' : '开始上传并处理' }}
        </button>
      </section>
    </Transition>

    <Transition name="panel-slide">
      <section v-if="activeMode === 'weblink'" class="ingest-panel">
        <div class="panel-head">
          <span class="panel-tag">WEB · LINK</span>
          <button type="button" class="panel-close" @click="activeMode = null">收起</button>
        </div>
        <textarea
          v-model="url"
          class="eva-textarea"
          rows="4"
          placeholder="粘贴视频链接，例如 B 站 / YouTube / mp4 直链"
        />

        <div v-if="preview" class="preview-box">
          <p><strong>{{ preview.title }}</strong></p>
          <p v-if="preview.durationSec">时长约 {{ formatDuration(preview.durationSec) }}</p>
          <p v-if="preview.platform">平台：{{ preview.platform }}</p>
          <p v-for="(w, i) in preview.warnings || []" :key="i" class="preview-warn">{{ w }}</p>
        </div>

        <div class="action-row">
          <button
            type="button"
            class="eva-submit secondary"
            :disabled="!url.trim() || previewing"
            @click="runPreview"
          >
            {{ previewing ? '预览中…' : '预览信息' }}
          </button>
          <button
            type="button"
            class="eva-submit"
            :disabled="!url.trim() || importing"
            @click="submitUrl"
          >
            {{ importing ? '导入中…' : '导入并处理' }}
          </button>
        </div>
      </section>
    </Transition>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Link, UploadFilled } from '@element-plus/icons-vue'
import { uploadVideo, importUrl, previewImportUrl } from '../api/video'
import { CHUNK_SIZE, resumableUpload } from '../api/upload'

const router = useRouter()
const activeMode = ref(null)
const selectedFile = ref(null)
const url = ref('')
const preview = ref(null)
const uploading = ref(false)
const importing = ref(false)
const previewing = ref(false)
const uploadProgress = ref(0)

function toggleMode(mode) {
  activeMode.value = activeMode.value === mode ? null : mode
}

function handleFileChange(file) {
  selectedFile.value = file.raw
}

function navigateAfterVideo(data) {
  ElMessage.success('任务已创建，正在处理')
  router.push(`/videos/${data.id}`)
}

async function submitUpload() {
  if (!selectedFile.value) return
  uploading.value = true
  uploadProgress.value = 0
  try {
    const file = selectedFile.value
    if (file.size > CHUNK_SIZE) {
      const result = await resumableUpload(file, {
        onProgress: (p) => { uploadProgress.value = p }
      })
      navigateAfterVideo(result.video)
    } else {
      const { data } = await uploadVideo(file)
      navigateAfterVideo(data)
    }
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    uploading.value = false
    uploadProgress.value = 0
  }
}

async function runPreview() {
  previewing.value = true
  preview.value = null
  try {
    const { data } = await previewImportUrl(url.value.trim())
    preview.value = data
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    previewing.value = false
  }
}

async function submitUrl() {
  importing.value = true
  try {
    const { data } = await importUrl(url.value.trim())
    ElMessage.success('导入任务已创建')
    router.push(`/videos/${data.id}`)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    importing.value = false
  }
}

function formatDuration(sec) {
  return `${Math.floor(sec / 60)}:${String(sec % 60).padStart(2, '0')}`
}
</script>

<style scoped>
.eva-upload {
  position: relative;
  max-width: 920px;
  margin: 0 auto;
  padding: 48px 24px 80px;
  min-height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
  align-items: center;
}

.eva-bg-grid {
  position: fixed;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(118, 255, 3, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(118, 255, 3, 0.03) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(ellipse 70% 60% at 50% 30%, black, transparent);
}

.eva-header {
  text-align: center;
  margin-bottom: 48px;
  position: relative;
  z-index: 1;
}

.eva-tag {
  margin: 0 0 12px;
  font-size: 11px;
  letter-spacing: 0.35em;
  color: var(--eva-green);
  font-family: 'Consolas', 'Courier New', monospace;
}

.eva-title {
  margin: 0 0 8px;
  font-size: 32px;
  font-weight: 700;
  color: #f5f5f7;
  letter-spacing: 0.08em;
}

.eva-subtitle {
  margin: 0 0 8px;
  color: rgba(118, 255, 3, 0.85);
  font-size: 15px;
  letter-spacing: 0.12em;
}

.eva-desc {
  margin: 0;
  color: rgba(245, 245, 247, 0.45);
  font-size: 14px;
  max-width: 520px;
  margin-left: auto;
  margin-right: auto;
  line-height: 1.6;
}

.portal-row {
  display: flex;
  align-items: stretch;
  justify-content: center;
  gap: 0;
  width: 100%;
  max-width: 720px;
  position: relative;
  z-index: 1;
}

.portal-card {
  flex: 1;
  max-width: 300px;
  padding: 32px 24px 28px;
  background: rgba(18, 12, 28, 0.85);
  border: 1px solid rgba(106, 27, 154, 0.6);
  cursor: pointer;
  text-align: center;
  transition: border-color 0.25s, box-shadow 0.25s, transform 0.2s;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  color: inherit;
  font: inherit;
}

.portal-card:first-child {
  border-radius: 4px 0 0 4px;
}

.portal-card:last-child {
  border-radius: 0 4px 4px 0;
}

.portal-card:hover {
  border-color: var(--eva-green);
  box-shadow: 0 0 24px rgba(118, 255, 3, 0.15);
  transform: translateY(-2px);
}

.portal-card.active {
  border-color: var(--eva-orange);
  box-shadow:
    0 0 0 1px var(--eva-orange),
    0 0 32px rgba(255, 145, 0, 0.25);
  background: rgba(30, 18, 45, 0.95);
}

.portal-index {
  font-size: 10px;
  letter-spacing: 0.2em;
  color: var(--eva-orange);
  font-family: 'Consolas', monospace;
}

.portal-icon {
  margin: 12px 0 8px;
  color: var(--eva-green);
}

.portal-card.active .portal-icon {
  color: var(--eva-orange);
}

.portal-label {
  font-size: 11px;
  letter-spacing: 0.25em;
  color: rgba(118, 255, 3, 0.7);
  font-family: 'Consolas', monospace;
}

.portal-name {
  font-size: 20px;
  font-weight: 600;
  color: #fff;
}

.portal-desc {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.45);
  margin-top: 4px;
}

.portal-divider {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  flex-shrink: 0;
  font-size: 10px;
  letter-spacing: 0.15em;
  color: rgba(255, 255, 255, 0.25);
  font-family: 'Consolas', monospace;
}

.ingest-panel {
  width: 100%;
  max-width: 560px;
  margin-top: 32px;
  padding: 24px;
  background: rgba(14, 10, 22, 0.92);
  border: 1px solid rgba(118, 255, 3, 0.35);
  border-radius: 4px;
  position: relative;
  z-index: 1;
  box-shadow: 0 0 40px rgba(106, 27, 154, 0.2);
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.panel-tag {
  font-size: 11px;
  letter-spacing: 0.2em;
  color: var(--eva-green);
  font-family: 'Consolas', monospace;
}

.panel-close {
  background: none;
  border: 1px solid rgba(255, 255, 255, 0.2);
  color: rgba(255, 255, 255, 0.6);
  padding: 4px 12px;
  font-size: 12px;
  cursor: pointer;
  border-radius: 2px;
}

.panel-close:hover {
  border-color: var(--eva-orange);
  color: var(--eva-orange);
}

.eva-uploader :deep(.el-upload-dragger) {
  background: rgba(0, 0, 0, 0.35);
  border: 1px dashed rgba(118, 255, 3, 0.4);
  border-radius: 4px;
}

.eva-uploader :deep(.el-upload-dragger:hover) {
  border-color: var(--eva-green);
}

.eva-uploader :deep(.el-icon--upload) {
  color: var(--eva-green);
}

.eva-uploader :deep(.el-upload__text) {
  color: rgba(255, 255, 255, 0.75);
}

.eva-uploader :deep(.el-upload__tip) {
  color: rgba(255, 255, 255, 0.4);
}

.file-chip {
  margin-top: 12px;
  padding: 8px 12px;
  background: rgba(106, 27, 154, 0.3);
  border-left: 3px solid var(--eva-orange);
  font-size: 13px;
  color: rgba(255, 255, 255, 0.85);
  word-break: break-all;
}

.progress-wrap {
  margin-top: 16px;
}

.progress-bar {
  height: 8px;
  background: rgba(0, 0, 0, 0.4);
  border-radius: 4px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: var(--eva-green);
  transition: width 0.2s;
}

.progress-text {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
  text-align: center;
}

.eva-textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 14px;
  background: rgba(0, 0, 0, 0.4);
  border: 1px solid rgba(118, 255, 3, 0.35);
  border-radius: 4px;
  color: #fff;
  font-size: 14px;
  line-height: 1.6;
  resize: vertical;
  font-family: inherit;
}

.eva-textarea:focus {
  outline: none;
  border-color: var(--eva-green);
  box-shadow: 0 0 12px rgba(118, 255, 3, 0.15);
}

.eva-textarea::placeholder {
  color: rgba(255, 255, 255, 0.35);
}

.panel-hint {
  margin: 12px 0 0;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.4);
}

.field-row {
  margin-top: 14px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.field-label {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.65);
}

.eva-select {
  flex: 1;
  padding: 8px 10px;
  background: rgba(0, 0, 0, 0.4);
  border: 1px solid rgba(118, 255, 3, 0.35);
  color: #fff;
  border-radius: 4px;
}

.cookie-panel {
  margin-top: 16px;
  padding: 12px;
  border: 1px dashed rgba(255, 145, 0, 0.35);
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.25);
}

.cookie-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.75);
}

.cookie-head a {
  color: var(--eva-green);
  font-size: 12px;
}

.cookie-text {
  margin-top: 10px;
}

.cookie-ok-hint {
  font-size: 13px;
  color: rgba(118, 255, 3, 0.85);
  line-height: 1.5;
}

.cookie-maintain {
  margin-top: 16px;
  padding: 12px;
  border: 1px solid rgba(106, 27, 154, 0.45);
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.2);
}

.cookie-maintain summary {
  cursor: pointer;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.8);
  user-select: none;
}

.maintain-hint {
  margin: 10px 0 8px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.45);
  line-height: 1.5;
}

.maintain-status {
  margin-bottom: 10px;
  padding: 8px 10px;
  font-size: 12px;
  border-left: 3px solid rgba(255, 255, 255, 0.25);
  background: rgba(255, 255, 255, 0.04);
  color: rgba(255, 255, 255, 0.75);
}

.maintain-status.status-ok {
  border-left-color: var(--eva-green);
  color: rgba(118, 255, 3, 0.85);
}

.maintain-status.status-warn {
  border-left-color: #ffcc80;
  color: #ffcc80;
}

.maintain-status.status-expired,
.maintain-status.status-missing {
  border-left-color: var(--eva-red);
  color: #ff8a80;
}

.maintain-save {
  margin-top: 10px;
}

.preview-box {
  margin-top: 14px;
  padding: 12px;
  border-left: 3px solid var(--eva-green);
  background: rgba(118, 255, 3, 0.06);
  font-size: 13px;
  color: rgba(255, 255, 255, 0.8);
}

.preview-warn {
  color: #ffcc80;
  margin: 6px 0 0;
}

.action-row {
  display: flex;
  gap: 10px;
  margin-top: 20px;
}

.action-row .eva-submit {
  margin-top: 0;
  flex: 1;
}

.eva-submit.secondary {
  border-color: rgba(255, 255, 255, 0.35);
  color: rgba(255, 255, 255, 0.75);
  background: rgba(255, 255, 255, 0.05);
}

.eva-submit {
  width: 100%;
  margin-top: 20px;
  padding: 14px;
  background: linear-gradient(180deg, rgba(118, 255, 3, 0.15), rgba(118, 255, 3, 0.05));
  border: 1px solid var(--eva-green);
  color: var(--eva-green);
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.12em;
  cursor: pointer;
  border-radius: 2px;
  transition: background 0.2s, box-shadow 0.2s;
}

.eva-submit:hover:not(:disabled) {
  background: rgba(118, 255, 3, 0.2);
  box-shadow: 0 0 20px rgba(118, 255, 3, 0.25);
}

.eva-submit:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.panel-slide-enter-active,
.panel-slide-leave-active {
  transition: opacity 0.25s, transform 0.25s;
}

.panel-slide-enter-from,
.panel-slide-leave-to {
  opacity: 0;
  transform: translateY(-12px);
}

@media (max-width: 640px) {
  .portal-row {
    flex-direction: column;
    align-items: center;
    gap: 16px;
  }

  .portal-card {
    max-width: 100%;
    width: 100%;
    border-radius: 4px !important;
  }

  .portal-divider {
    width: 100%;
    padding: 8px 0;
  }
}
</style>
