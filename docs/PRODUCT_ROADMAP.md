# 听悟 HearMind — 产品需求与迭代路线图

> 版本：v1.0-plan  
> License：MIT  
> 状态：按 Phase P0–P5 迭代  

---

## 1. 产品定位

**一句话（听悟 HearMind）**：听懂每一帧，悟透每一个问题 —— 外链/本地音视频 → 带时间戳转写 → 场景化摘要 → 知识增强问答 → 导出 Markdown 笔记；可自部署的开源学习工具。

**不做（v1）**：网盘同步、视频画面/PPT 理解、移动端 App、付费体系、OSS（v1 固定本地存储）。

**差异化**：B 站/YouTube 直导、用户自带 Cookie、RAG 可引用、Markdown 导出、ECS 私有化。

---

## 2. 现状基线

| 模块 | 状态 |
|------|------|
| JWT 鉴权、用户隔离 | 完成 |
| 本地上传 + 分片断点续传 | 完成 |
| Web Link（yt-dlp） | 完成（P2 增强 Cookie） |
| ASR + 摘要 + RAG 问答 | P4 完成 |
| Redis 限流 + Redis 问答会话 | 完成 |
| 去重、指数退避、本地存储 | 完成 |

---

## 3. 默认决策

| 项 | 默认 |
|----|------|
| 迭代顺序 | P0 → P1 → P2 → P3 → P4 → P5 |
| License | MIT |
| RAG 向量存储 | MySQL `transcript_chunk.embedding_json` |
| Cookie | 用户导入时与 URL 一并上传；服务器 config 仅 fallback |
| Cookie 持久化 | v1 仅当次任务临时文件 |
| 上线 | 阿里云 ECS + Nginx；v1 本地存储 |
| 内容类型默认 | `GENERAL` |

---

## 4. 七大需求与验收

### 需求 1：GitHub 开源与迭代

- `README.md`、`CHANGELOG.md`、`LICENSE`（MIT）
- Commit 规范：`feat|fix|docs|chore|refactor`
- 每 Phase 打 Git tag

### 需求 2：RAG 问答 + 分段索引

- Flyway V5 `transcript_chunk`
- DashScope Embedding + Top-K 检索
- `ChatResponse.citations[]`

### 需求 3：Web Link + 用户 Cookie

- `POST /import-url/preview` multipart（url + cookieFile/cookieText）
- 任务级临时 Cookie；引导见 [COOKIE.md](COOKIE.md)

### 需求 4：转写时间戳 + 跳转

- `TranscriptResponse.segments[]`
- 前端点击跳转播放器

### 需求 5：摘要可编辑 + 导出 MD

- `PUT /summary`、`GET /export/markdown`

### 需求 6：场景化摘要模板

- `video.content_type`：GENERAL / CLASS / MEETING / INTERVIEW
- `POST /summary/regenerate`

### 需求 7：ECS 上线

- 见 [DEPLOY.md](DEPLOY.md)

---

## 5. 迭代排期

| Phase | Tag | 内容 |
|-------|-----|------|
| P0 | v0.2.0 | 开源文档 |
| P1 | v0.3.0 | 时间戳转写 |
| P2 | v0.4.0 | Web Link + Cookie |
| P3 | v0.5.0 | 摘要模板/编辑/导出 |
| P4 | v0.6.0 | RAG + citations |
| P5 | v1.0.0 | 部署文档 |

---

## 6. 目录结构

```text
docs/PRODUCT_ROADMAP.md  # 本文档
docs/COOKIE.md
docs/DEPLOY.md
docs/eval/qa_cases.json
deploy/nginx.conf
deploy/hearmind.service
```
