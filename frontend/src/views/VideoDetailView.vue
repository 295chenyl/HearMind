<template>
  <div class="detail-page" v-loading="loading">
    <div v-if="video?.status === 'FAILED'" class="alert-bar error">
      <span>{{ video.errorMessage || '处理失败' }}</span>
      <button type="button" class="alert-action" :disabled="retrying" @click="retryProcess">重新处理</button>
    </div>
    <div v-else-if="video?.status === 'PENDING'" class="alert-bar warn">
      <span>视频仍在排队或处理中</span>
      <button type="button" class="alert-action" :disabled="retrying" @click="retryProcess">重新处理</button>
    </div>
    <div v-else-if="video && video.status !== 'READY'" class="alert-bar info">
      <span>当前状态：{{ statusText(video.status) }}，页面将自动刷新…</span>
    </div>

    <header v-if="video" class="detail-header">
      <p class="detail-tag">HEARMIND · STUDIO</p>
      <h1 class="detail-title">{{ video.title }}</h1>
    </header>

    <div class="workspace">
      <section class="player-pane">
        <div class="player-wrap">
          <video
            v-if="video?.status === 'READY'"
            ref="videoRef"
            class="player"
            controls
            :src="streamUrl"
          />
          <div v-else class="player-placeholder">
            <p>视频处理完成后可播放</p>
          </div>
        </div>
      </section>

      <section class="content-pane">
        <div class="mode-tabs">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            type="button"
            class="mode-tab"
            :class="{ active: activeMode === tab.key }"
            @click="activeMode = tab.key"
          >
            {{ tab.label }}
          </button>
        </div>

        <div class="mode-body">
          <div v-show="activeMode === 'transcript'" class="mode-panel">
            <el-scrollbar v-if="transcript" class="scroll-area">
              <div v-if="transcript.segments?.length" class="segment-list">
                <button
                  v-for="(seg, idx) in transcript.segments"
                  :key="idx"
                  type="button"
                  class="segment-row"
                  @click="seekTo(seg.startMs)"
                >
                  <span class="segment-time">{{ formatMs(seg.startMs) }}</span>
                  <span class="segment-text">{{ seg.text }}</span>
                </button>
              </div>
              <pre v-else class="transcript">{{ transcript.fullText }}</pre>
            </el-scrollbar>
            <div v-else class="empty-panel">转写进行中…</div>
          </div>

          <div v-show="activeMode === 'summary'" class="mode-panel">
            <div v-if="summary" class="summary-toolbar">
              <template v-if="!summaryEditing">
                <button type="button" class="toolbar-btn" @click="startSummaryEdit">编辑</button>
                <a class="toolbar-btn link" :href="exportUrl" target="_blank" rel="noopener">导出 Markdown</a>
              </template>
              <template v-else>
                <button type="button" class="toolbar-btn" :disabled="savingSummary" @click="saveSummary">
                  {{ savingSummary ? '保存中…' : '保存' }}
                </button>
                <button type="button" class="toolbar-btn" @click="cancelSummaryEdit">取消</button>
              </template>
            </div>
            <el-scrollbar v-if="summary" class="scroll-area">
              <div
                v-if="!summaryEditing"
                class="markdown-body"
                v-html="summaryHtml"
              />
              <textarea
                v-else
                v-model="summaryDraft"
                class="summary-editor"
                rows="16"
              />
            </el-scrollbar>
            <div v-else class="empty-panel">摘要生成中…</div>
          </div>

          <div v-show="activeMode === 'chat'" class="mode-panel chat-panel">
            <div ref="chatBoxRef" class="chat-stream">
              <div v-if="!messages.length" class="chat-empty">
                <p>听悟 · 知识增强问答</p>
                <span>可问具体问题，或输入「总结/概括」获取结构化摘要</span>
              </div>
              <div
                v-for="(msg, idx) in messages"
                :key="idx"
                class="bubble-row"
                :class="msg.role"
              >
                <div class="bubble">
                  <div v-if="msg.role === 'assistant'" class="markdown-body chat-markdown" v-html="renderMarkdown(msg.content)" />
                  <div v-else class="bubble-text">{{ msg.content }}</div>
                  <div v-if="msg.citations?.length" class="citations">
                    <button
                      v-for="(c, cIdx) in msg.citations"
                      :key="cIdx"
                      type="button"
                      class="citation-chip"
                      @click="seekTo(c.startSec * 1000)"
                    >
                      {{ formatSec(c.startSec) }} · {{ c.quote }}
                    </button>
                  </div>
                </div>
              </div>
              <div v-if="chatting && !streamReplyStarted" class="bubble-row assistant">
                <div class="bubble typing">思考中…</div>
              </div>
            </div>
            <div class="chat-compose">
              <textarea
                v-model="question"
                class="chat-input"
                rows="2"
                placeholder="输入问题，Ctrl+Enter 发送"
                :disabled="video?.status !== 'READY' || chatting"
                @keydown.ctrl.enter.prevent="sendMessage"
              />
              <button
                type="button"
                class="chat-send"
                :disabled="video?.status !== 'READY' || !question.trim() || chatting"
                @click="sendMessage"
              >
                发送
              </button>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  chatStream,
  exportMarkdown,
  getChatHistory,
  getSummary,
  getTranscript,
  getVideo,
  getVideoStreamUrl,
  retryVideo,
  updateSummary
} from '../api/video'
import { renderMarkdown } from '../utils/markdown'

const route = useRoute()
const videoId = computed(() => route.params.id)

const tabs = [
  { key: 'transcript', label: '转写' },
  { key: 'summary', label: '摘要' },
  { key: 'chat', label: '问答' }
]

const video = ref(null)
const transcript = ref(null)
const summary = ref(null)
const loading = ref(false)
const activeMode = ref('transcript')
const question = ref('')
const messages = ref([])
const sessionId = ref(null)
const chatting = ref(false)
const streamReplyStarted = ref(false)
const retrying = ref(false)
const chatBoxRef = ref(null)
const videoRef = ref(null)
const summaryDraft = ref('')
const summaryEditing = ref(false)
const savingSummary = ref(false)
let pollTimer = null
let chatAbortController = null

const streamUrl = computed(() => getVideoStreamUrl(videoId.value))
const exportUrl = computed(() => exportMarkdown(videoId.value))
const summaryHtml = computed(() => renderMarkdown(summary.value?.content || ''))

const statusMap = {
  PENDING: '等待中',
  DOWNLOADING: '下载中',
  TRANSCRIBING: '转写中',
  SUMMARIZING: '生成摘要',
  READY: '已完成',
  FAILED: '失败'
}

function sessionStorageKey() {
  return `hearmind:session:${videoId.value}`
}

function statusText(status) {
  return statusMap[status] || status
}

async function loadVideo() {
  const { data } = await getVideo(videoId.value)
  video.value = data
  return data
}

function citationsStorageKey() {
  return `hearmind:citations:${videoId.value}:${sessionId.value || 'none'}`
}

function saveCitations(citations) {
  if (!sessionId.value || !citations?.length) return
  try {
    localStorage.setItem(citationsStorageKey(), JSON.stringify(citations))
  } catch {
    // ignore
  }
}

function loadSavedCitations() {
  try {
    const raw = localStorage.getItem(citationsStorageKey())
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function attachSavedCitations() {
  const saved = loadSavedCitations()
  if (!saved?.length || !messages.value.length) return
  for (let i = messages.value.length - 1; i >= 0; i--) {
    if (messages.value[i].role === 'assistant') {
      messages.value[i].citations = saved
      break
    }
  }
}

async function loadChatHistory() {
  try {
    const { data } = await getChatHistory(videoId.value)
    if (data.sessionId) {
      sessionId.value = data.sessionId
      localStorage.setItem(sessionStorageKey(), String(data.sessionId))
    }
    if (data.history?.length) {
      messages.value = data.history.map((m) => ({
        role: m.role,
        content: m.content,
        citations: m.citations || []
      }))
      attachSavedCitations()
    }
  } catch {
    const cached = localStorage.getItem(sessionStorageKey())
    if (cached) sessionId.value = Number(cached)
  }
}

async function loadExtras() {
  const [t, s] = await Promise.allSettled([
    getTranscript(videoId.value),
    getSummary(videoId.value)
  ])
  if (t.status === 'fulfilled') transcript.value = t.value.data
  if (s.status === 'fulfilled') {
    summary.value = s.value.data
    summaryDraft.value = s.value.data.content || ''
    summaryEditing.value = false
  }
  await loadChatHistory()
}

async function refresh() {
  loading.value = true
  try {
    const data = await loadVideo()
    if (data.status === 'READY') {
      await loadExtras()
      stopPoll()
    } else if (data.status === 'FAILED') {
      stopPoll()
    } else {
      startPoll()
    }
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function startPoll() {
  if (pollTimer) return
  pollTimer = setInterval(async () => {
    try {
      const data = await loadVideo()
      if (data.status === 'READY') {
        await loadExtras()
        stopPoll()
      } else if (data.status === 'FAILED') {
        stopPoll()
      }
    } catch {
      // silent poll
    }
  }, 4000)
}

function stopPoll() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function retryProcess() {
  retrying.value = true
  try {
    await retryVideo(videoId.value)
    ElMessage.success('已重新开始处理')
    startPoll()
    await refresh()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    retrying.value = false
  }
}

async function sendMessage() {
  if (!question.value.trim()) return
  chatting.value = true
  streamReplyStarted.value = false
  const content = question.value.trim()
  question.value = ''
  const messageStartIndex = messages.value.length
  messages.value.push({ role: 'user', content })
  await nextTick()
  scrollChat()

  let assistantMessage = null
  let streamCitations = []
  const controller = new AbortController()
  chatAbortController = controller

  try {
    await chatStream(videoId.value, content, sessionId.value, (event, data) => {
      if (event === 'meta') {
        sessionId.value = data.sessionId
        streamCitations = data.citations || []
        localStorage.setItem(sessionStorageKey(), String(data.sessionId))
        return
      }
      if (event === 'delta') {
        if (!assistantMessage) {
          assistantMessage = { role: 'assistant', content: '', citations: streamCitations }
          messages.value.push(assistantMessage)
          streamReplyStarted.value = true
        }
        assistantMessage.content += data.content || ''
        nextTick(scrollChat)
        return
      }
      if (event === 'done') {
        applyCompletedChat(data)
      }
    }, { signal: controller.signal })
    await nextTick()
    scrollChat()
  } catch (e) {
    messages.value.splice(messageStartIndex)
    if (e.name !== 'AbortError') {
      ElMessage.error(e.message)
    }
  } finally {
    if (chatAbortController === controller) {
      chatAbortController = null
    }
    chatting.value = false
    streamReplyStarted.value = false
  }
}

function applyCompletedChat(data) {
  sessionId.value = data.sessionId
  localStorage.setItem(sessionStorageKey(), String(data.sessionId))
  messages.value = data.history.map((message, index) => ({
    role: message.role,
    content: message.content,
    citations: index === data.history.length - 1 && message.role === 'assistant'
      ? (data.citations || [])
      : (message.citations || [])
  }))
  saveCitations(data.citations)
}

function scrollChat() {
  if (chatBoxRef.value) {
    chatBoxRef.value.scrollTop = chatBoxRef.value.scrollHeight
  }
}

function formatMs(ms) {
  const totalSec = Math.floor(ms / 1000)
  return formatSec(totalSec)
}

function formatSec(totalSec) {
  const m = Math.floor(totalSec / 60)
  const s = totalSec % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

function seekTo(startMs) {
  if (!videoRef.value) return
  videoRef.value.currentTime = startMs / 1000
  videoRef.value.play().catch(() => {})
}

function startSummaryEdit() {
  summaryDraft.value = summary.value?.content || ''
  summaryEditing.value = true
}

function cancelSummaryEdit() {
  summaryDraft.value = summary.value?.content || ''
  summaryEditing.value = false
}

async function saveSummary() {
  savingSummary.value = true
  try {
    const { data } = await updateSummary(videoId.value, summaryDraft.value)
    summary.value = data
    summaryEditing.value = false
    ElMessage.success('摘要已保存')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    savingSummary.value = false
  }
}

watch(activeMode, (mode) => {
  if (mode === 'chat') nextTick(scrollChat)
})

watch(videoId, () => {
  chatAbortController?.abort()
  messages.value = []
  sessionId.value = null
  summaryDraft.value = ''
  summaryEditing.value = false
  activeMode.value = 'transcript'
  refresh()
})

onMounted(refresh)
onUnmounted(() => {
  chatAbortController?.abort()
  stopPoll()
})
</script>

<style scoped>
.detail-page {
  max-width: 1280px;
  margin: 0 auto;
  padding: 12px 16px 24px;
  min-height: calc(100vh - 60px);
}

.detail-header {
  margin-bottom: 16px;
}

.detail-tag {
  margin: 0 0 6px;
  font-size: 10px;
  letter-spacing: 0.28em;
  color: var(--eva-green);
  font-family: Consolas, monospace;
}

.detail-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #f5f5f7;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.alert-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  margin-bottom: 12px;
  border-radius: 2px;
  font-size: 13px;
}

.alert-bar.error {
  background: rgba(213, 0, 0, 0.15);
  border: 1px solid rgba(213, 0, 0, 0.4);
  color: #ff8a80;
}

.alert-bar.warn {
  background: rgba(255, 145, 0, 0.12);
  border: 1px solid rgba(255, 145, 0, 0.35);
  color: #ffcc80;
}

.alert-bar.info {
  background: rgba(106, 27, 154, 0.2);
  border: 1px solid rgba(118, 255, 3, 0.25);
  color: rgba(255, 255, 255, 0.75);
}

.alert-action {
  flex-shrink: 0;
  background: transparent;
  border: 1px solid currentColor;
  color: inherit;
  padding: 4px 12px;
  cursor: pointer;
  border-radius: 2px;
  font-size: 12px;
}

.workspace {
  display: grid;
  grid-template-columns: 1.15fr 0.85fr;
  gap: 16px;
  align-items: stretch;
  min-height: calc(100vh - 100px);
}

.player-pane,
.content-pane {
  background: rgba(14, 10, 22, 0.92);
  border: 1px solid rgba(106, 27, 154, 0.5);
  border-radius: 4px;
  overflow: hidden;
}

.player-wrap {
  padding: 12px;
}

.player {
  width: 100%;
  max-height: calc(100vh - 100px);
  min-height: 320px;
  background: #000;
  border-radius: 2px;
  display: block;
}

.player-placeholder {
  min-height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.45);
  border: 1px dashed rgba(118, 255, 3, 0.2);
  border-radius: 2px;
  color: rgba(255, 255, 255, 0.45);
  font-size: 14px;
}

.mode-tabs {
  display: flex;
  border-bottom: 1px solid rgba(118, 255, 3, 0.15);
}

.mode-tab {
  flex: 1;
  padding: 14px 8px;
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  color: rgba(255, 255, 255, 0.5);
  font-size: 14px;
  cursor: pointer;
  transition: color 0.2s, border-color 0.2s;
}

.mode-tab:hover {
  color: rgba(255, 255, 255, 0.8);
}

.mode-tab.active {
  color: var(--eva-green);
  border-bottom-color: var(--eva-green);
  background: rgba(118, 255, 3, 0.05);
}

.mode-body {
  height: calc(100vh - 100px);
  min-height: 400px;
  display: flex;
  flex-direction: column;
}

.mode-panel {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.scroll-area {
  flex: 1;
  padding: 16px;
}

.transcript {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.75;
  margin: 0;
  color: rgba(255, 255, 255, 0.82);
  font-family: inherit;
}

.segment-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.segment-row {
  display: flex;
  gap: 12px;
  width: 100%;
  text-align: left;
  padding: 10px 12px;
  background: rgba(0, 0, 0, 0.25);
  border: 1px solid rgba(118, 255, 3, 0.15);
  border-radius: 4px;
  cursor: pointer;
  color: inherit;
  font: inherit;
}

.segment-row:hover {
  border-color: var(--eva-green);
  background: rgba(118, 255, 3, 0.06);
}

.segment-time {
  flex-shrink: 0;
  font-family: 'Consolas', monospace;
  color: var(--eva-orange);
  font-size: 12px;
}

.segment-text {
  font-size: 13px;
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.85);
}

.summary-toolbar {
  display: flex;
  gap: 8px;
  padding: 10px 16px 0;
  flex-wrap: wrap;
  align-items: center;
}

.toolbar-btn {
  padding: 6px 12px;
  font-size: 12px;
  background: rgba(118, 255, 3, 0.08);
  border: 1px solid rgba(118, 255, 3, 0.35);
  color: var(--eva-green);
  border-radius: 2px;
  cursor: pointer;
  text-decoration: none;
}

.toolbar-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.summary-editor {
  width: 100%;
  box-sizing: border-box;
  min-height: 360px;
  padding: 12px;
  background: rgba(0, 0, 0, 0.35);
  border: 1px solid rgba(118, 255, 3, 0.2);
  border-radius: 4px;
  color: rgba(255, 255, 255, 0.88);
  font-size: 14px;
  line-height: 1.7;
  resize: vertical;
  font-family: inherit;
}

.empty-panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255, 255, 255, 0.4);
  font-size: 14px;
}

.markdown-body {
  line-height: 1.75;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.85);
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  margin: 1em 0 0.5em;
  font-weight: 600;
  color: var(--eva-green);
}

.markdown-body :deep(p) {
  margin: 0.6em 0;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 0.5em 0;
  padding-left: 1.5em;
}

.markdown-body :deep(code) {
  padding: 0.15em 0.4em;
  background: rgba(0, 0, 0, 0.35);
  border-radius: 2px;
  color: var(--eva-orange);
}

.markdown-body :deep(blockquote) {
  margin: 0.6em 0;
  padding-left: 12px;
  border-left: 3px solid var(--eva-orange);
  color: rgba(255, 255, 255, 0.65);
}

.markdown-body :deep(a) {
  color: var(--eva-green);
}

.chat-panel {
  padding: 0;
}

.chat-stream {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.chat-empty {
  margin: auto;
  text-align: center;
  color: rgba(255, 255, 255, 0.35);
}

.chat-empty p {
  margin: 0 0 6px;
  font-size: 15px;
  color: rgba(255, 255, 255, 0.55);
}

.chat-empty span {
  font-size: 12px;
}

.bubble-row {
  display: flex;
  max-width: 88%;
}

.bubble-row.user {
  align-self: flex-end;
}

.bubble-row.assistant {
  align-self: flex-start;
}

.bubble {
  padding: 10px 14px;
  border-radius: 12px;
  line-height: 1.65;
  font-size: 14px;
  word-break: break-word;
}

.bubble-text {
  white-space: pre-wrap;
}

.chat-markdown :deep(p) {
  margin: 0.4em 0;
}

.chat-markdown :deep(p:first-child) {
  margin-top: 0;
}

.chat-markdown :deep(p:last-child) {
  margin-bottom: 0;
}

.chat-markdown :deep(ul),
.chat-markdown :deep(ol) {
  margin: 0.4em 0;
  padding-left: 1.4em;
}

.chat-markdown :deep(code) {
  padding: 0.1em 0.35em;
  background: rgba(0, 0, 0, 0.3);
  border-radius: 2px;
  font-size: 0.92em;
}

.chat-markdown :deep(pre) {
  margin: 0.5em 0;
  padding: 8px 10px;
  background: rgba(0, 0, 0, 0.35);
  border-radius: 4px;
  overflow-x: auto;
}

.chat-markdown :deep(pre code) {
  padding: 0;
  background: transparent;
}

.bubble-row.user .bubble {
  background: rgba(255, 145, 0, 0.18);
  border: 1px solid rgba(255, 145, 0, 0.35);
  color: #fff;
  border-top-right-radius: 4px;
}

.bubble-row.assistant .bubble {
  background: rgba(106, 27, 154, 0.35);
  border: 1px solid rgba(118, 255, 3, 0.25);
  color: rgba(255, 255, 255, 0.9);
  border-top-left-radius: 4px;
}

.citations {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 10px;
}

.citation-chip {
  text-align: left;
  padding: 6px 8px;
  font-size: 11px;
  background: rgba(0, 0, 0, 0.25);
  border: 1px solid rgba(255, 145, 0, 0.35);
  color: #ffcc80;
  border-radius: 4px;
  cursor: pointer;
}

.bubble.typing {
  color: rgba(255, 255, 255, 0.5);
  font-style: italic;
}

.chat-compose {
  display: flex;
  gap: 10px;
  padding: 12px 16px;
  border-top: 1px solid rgba(118, 255, 3, 0.15);
  background: rgba(0, 0, 0, 0.25);
}

.chat-input {
  flex: 1;
  resize: none;
  padding: 10px 12px;
  background: rgba(0, 0, 0, 0.35);
  border: 1px solid rgba(118, 255, 3, 0.3);
  border-radius: 4px;
  color: #fff;
  font-size: 14px;
  font-family: inherit;
  line-height: 1.5;
}

.chat-input:focus {
  outline: none;
  border-color: var(--eva-green);
}

.chat-send {
  flex-shrink: 0;
  padding: 0 20px;
  background: rgba(118, 255, 3, 0.12);
  border: 1px solid var(--eva-green);
  color: var(--eva-green);
  font-size: 13px;
  cursor: pointer;
  border-radius: 2px;
}

.chat-send:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

@media (max-width: 960px) {
  .workspace {
    grid-template-columns: 1fr;
  }

  .mode-body {
    height: 480px;
  }

  .player {
    min-height: 220px;
    max-height: 320px;
  }
}
</style>
