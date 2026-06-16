# 听悟 HearMind — 产品功能文档

> **听懂每一帧，悟透每一个问题。**
>
> 文档版本：2026-06-17  
> 项目状态：本地 / Docker 部署可正常运行

---

## 1. 产品定位

**听悟（HearMind）** 是一款面向个人与团队的音视频智能理解平台。用户可通过本地上传或粘贴链接导入音视频，系统自动完成转写、摘要与知识增强问答，帮助用户从长视频中快速提取、理解并复用信息。

**核心价值链路：**

```
接入（上传 / 链接） → 聆听（AI 转写） → 领悟（智能摘要） → 深问（RAG 问答） → 导出（Markdown 笔记）
```

**v1 明确不做：** 网盘同步、视频画面/PPT 理解、移动端 App、付费体系。存储默认本地，OSS 为可选扩展。

---

## 2. 用户与权限

### 2.1 账号体系

| 功能 | 说明 |
|------|------|
| 用户注册 | 邮箱 + 密码注册 |
| 用户登录 | 登录后获取 JWT，有效期默认 7 天 |
| 当前用户 | `GET /api/auth/me` 获取登录用户信息 |
| 路由守卫 | 除登录页外，前端页面均需登录后访问 |

### 2.2 数据隔离

- 每个用户只能访问自己上传或导入的视频
- 视频列表、详情、转写、摘要、问答均按用户隔离

### 2.3 接口限流

基于 Redis 令牌桶，对高频接口单独限流：

| 规则 | 典型场景 |
|------|----------|
| `chat` | 问答接口 |
| `import-url` | 链接导入 |
| `upload` | 整文件上传 / 断点续传初始化与完成 |
| `login` | 登录 |
| `default` | 其他 API |

分片上传的 chunk 请求不计入 upload 限流，避免大文件续传误触发。

---

## 3. 内容接入

### 3.1 本地上传

| 功能 | 说明 |
|------|------|
| 拖拽 / 选择上传 | 支持常见视频格式 |
| 整文件上传 | `POST /api/videos/upload`，适合小文件 |
| 分片断点续传 | 大文件自动分片，支持中断后续传 |
| 上传进度 | 前端展示百分比进度 |
| 时长限制 | 最长 50 分钟（3000 秒），超出拒绝处理 |
| 文件大小 | 单文件最大约 2 GB |

**断点续传流程：**

1. `POST /api/videos/upload/init` — 创建上传会话
2. `PUT /api/videos/upload/{sessionId}/chunks/{chunkIndex}` — 逐片上传
3. `POST /api/videos/upload/{sessionId}/complete` — 合并并完成
4. `GET /api/videos/upload/sessions` — 列出可续传会话

### 3.2 链接导入（Web Link）

| 功能 | 说明 |
|------|------|
| 支持平台 | B 站、YouTube、HTTP(S) 直链（如 mp4） |
| 预览信息 | `POST /api/videos/import-url/preview`，展示标题、时长、平台 |
| 一键导入 | `POST /api/videos/import-url`，创建处理任务 |
| 直链下载 | HTTP 直链直接下载，保留正确文件扩展名 |
| 平台下载 | 非直链 URL 通过 yt-dlp 下载，ffmpeg 处理音视频 |

**媒体处理流水线：**

```
URL / 本地文件
    ↓
【平台链接】yt-dlp 下载  /  【直链】HTTP 下载
    ↓
ffprobe 检测时长（≤ 50 分钟）
    ↓
ffmpeg 提取音频（WAV）
    ↓
DashScope Paraformer 语音转写
    ↓
通义千问生成摘要
    ↓
构建 RAG 索引 → 标记 READY
```

### 3.3 B 站 Cookie 支持

| 功能 | 说明 |
|------|------|
| 服务端 fallback | `config/bilibili.cookies.txt` 全局 Cookie |
| 健康检查 | `GET /api/system/import-health` |
| 前端提示 | 登录后顶部 Cookie 状态横幅（缺失 / 即将过期 / 已过期） |
| 浏览器 Cookie | 可配置从 Edge 等浏览器读取（`ytdlp-cookies-browser`） |

详见项目内 [docs/COOKIE.md](docs/COOKIE.md)。

### 3.4 内容去重（可选）

- 上传时可携带内容哈希（content hash）
- 若检测到相同哈希且已有 READY 视频，直接返回已有视频 ID，跳过重复处理
- 默认关闭，可通过 `app.dedup-enabled` 开启

---

## 4. 智能处理

### 4.1 异步任务与状态

视频处理为后台异步任务，状态流转如下：

| 状态 | 含义 |
|------|------|
| `PENDING` | 排队 / 等待处理 |
| `DOWNLOADING` | 下载或准备媒体文件 |
| `TRANSCRIBING` | 语音转写中 |
| `SUMMARIZING` | 生成摘要中 |
| `READY` | 处理完成，可使用全部功能 |
| `FAILED` | 处理失败，可查看错误并重试 |

- 详情页自动轮询刷新状态
- 失败或长时间 pending 时可点击「重新处理」

### 4.2 语音转写（ASR）

| 功能 | 说明 |
|------|------|
| 引擎 | 阿里云 DashScope Paraformer（`paraformer-v2`） |
| 输出 | 全文转写 + 带时间戳的分段列表 |
| 分段 API | `GET /api/videos/{id}/transcript` |
| 前端展示 | 转写 Tab，按时间戳分段显示 |

### 4.3 带时间戳跳转

- 转写列表每段显示 `MM:SS` 时间戳
- 点击任意分段，播放器跳转到对应位置
- 问答引用（citation）同样支持点击跳转

### 4.4 智能摘要

| 功能 | 说明 |
|------|------|
| 自动生成 | 转写完成后由通义千问（`qwen-plus`）生成 |
| 场景化模板 | 按内容类型使用不同 Prompt |
| 内容类型 | `GENERAL`（通用）、`CLASS`（课程）、`MEETING`（会议）、`INTERVIEW`（访谈） |
| 在线编辑 | 详情页摘要 Tab 支持编辑并保存 |
| 重新生成 | `POST /api/videos/{id}/summary/regenerate` |
| 修改类型 | `PUT /api/videos/{id}/content-type` 后重新生成摘要 |

### 4.5 RAG 知识增强问答

| 功能 | 说明 |
|------|------|
| 分段索引 | 转写文本按约 45 秒窗口切分，存入 `transcript_chunk` |
| 向量检索 | DashScope Embedding + 余弦相似度 Top-K |
| 关键词降级 | 语义检索为空时，关键词匹配转写片段兜底 |
| 多轮对话 | Redis 存储会话历史，TTL 默认 30 天 |
| 引用溯源 | 回答附带 citation（时间戳 + 原文片段），可点击跳转 |
| 总结模式 | 输入「总结 / 概括 / 主要讲了什么」触发结构化摘要输出 |
| 索引重建 | `POST /api/videos/{id}/index/rebuild`，修复旧数据索引 |

**问答 Prompt 能力：**

- 严格基于转写事实回答，拒绝幻觉
- 结合历史对话做指代消解
- 自动过滤口水词与时间戳噪声
- 按内容类型输出结构化摘要（概览、要点、行动项）

### 4.6 重试与容错

| 功能 | 说明 |
|------|------|
| 指数退避 | 外部 API 调用失败自动重试（最多 3 次） |
| 处理重试 | `POST /api/videos/{id}/retry` 重新跑完整流水线 |
| RAG 容错 | 索引构建失败不阻断 READY，可后续手动 rebuild |

---

## 5. 前端界面

### 5.1 页面结构

| 路由 | 页面 | 功能 |
|------|------|------|
| `/login` | 登录 / 注册 | 账号入口 |
| `/` | 导入页 | 本地上传 + 链接导入 |
| `/videos` | 视频库 | 已导入视频卡片列表 |
| `/videos/:id` | 详情工作室 | 播放器 + 转写 / 摘要 / 问答 |

### 5.2 详情工作室（三 Tab）

**转写 Tab**

- 分段列表或全文展示
- 点击时间戳跳转播放

**摘要 Tab**

- Markdown 渲染展示
- 编辑 / 保存
- 导出 Markdown 笔记

**问答 Tab**

- 多轮对话气泡
- Citation 芯片（点击跳转）
- Ctrl+Enter 快捷发送
- 刷新后恢复历史对话；citation 前端 localStorage 缓存

### 5.3 视频库

- 展示来源类型（本地上传 / 链接导入）
- 处理状态、平台、时长、创建时间
- 点击进入详情页

### 5.4 视觉风格

- 深色主题（EVA 风格配色）
- Element Plus 组件
- 响应式布局

---

## 6. 笔记导出

`GET /api/videos/{id}/export/markdown` 导出 Markdown 文件，包含：

- 视频标题、来源链接、时长
- 摘要正文
- 带时间戳的转写分段
- 最近一次问答会话历史（用户提问 + 助手回答）

前端摘要 Tab 提供「导出 Markdown」按钮，浏览器直接下载。

---

## 7. 视频播放

- `GET /api/videos/{id}/stream` 流式播放
- 本地存储：直接返回文件流
- OSS 存储（可选）：302 重定向到签名 URL
- 详情页内嵌 HTML5 `<video>` 播放器

---

## 8. 技术架构

### 8.1 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 + Vite + Element Plus |
| 后端 | Spring Boot 3 + Java 17 |
| 数据库 | MySQL 8 + Flyway 迁移 |
| 缓存 | Redis 7（限流 + 问答会话） |
| 媒体 | yt-dlp + ffmpeg / ffprobe |
| AI | DashScope（Paraformer ASR + 通义千问 LLM + Embedding） |
| 部署 | Docker Compose / systemd + Nginx |

### 8.2 数据库迁移（Flyway）

| 版本 | 内容 |
|------|------|
| V1 | 视频、转写、摘要等核心表 |
| V2 | 用户表 |
| V3 | 历史数据迁移 |
| V4 | 断点续传会话表 |
| V5 | RAG 转写分段索引表 |
| V6 | 内容类型与摘要扩展 |

### 8.3 配置要点

| 配置项 | 说明 |
|--------|------|
| `config/dashscope.key` | DashScope API Key（推荐文件方式） |
| `config/bilibili.cookies.txt` | B 站 Cookie（可选） |
| `config/database.yml` | 数据库连接（可选覆盖） |
| `config/redis.yml` | Redis 连接（可选覆盖） |
| `config/oss.yml` | 阿里云 OSS（默认关闭） |
| `app.max-duration-seconds` | 最大视频时长 3000 秒 |
| `auth.jwt-secret` | JWT 签名密钥（生产环境务必修改） |

### 8.4 存储

- **默认**：本地目录 `./storage/videos/{id}/`
- **可选**：阿里云 OSS（`oss.enabled: true` 时启用）

---

## 9. 部署方式

项目支持多种部署路径，均已提供文档与脚本：

| 方式 | 文档 / 脚本 | 说明 |
|------|---------------|------|
| 本地开发 | [README.md](README.md) | MySQL Docker + 本地启动前后端 |
| Docker Compose | [docs/DOCKER_DEPLOY.md](docs/DOCKER_DEPLOY.md) | 一键启动 MySQL / Redis / 后端 / 前端 |
| ECS 裸机 | [docs/DEPLOY.md](docs/DEPLOY.md) | systemd + Nginx |
| ECS 一键上传 | `deploy/deploy.ps1` | Windows 打包上传 + 远程构建 |

**Docker Compose 服务：**

- `hearmind-mysql` — 数据库
- `hearmind-redis` — 缓存
- `hearmind-backend` — Spring Boot（含 ffmpeg、yt-dlp）
- `hearmind-frontend` — Nginx 反代，对外 80 端口

---

## 10. API 一览

### 认证

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/register` | 注册 |
| POST | `/api/auth/login` | 登录 |
| GET | `/api/auth/me` | 当前用户 |

### 视频

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/videos/upload` | 整文件上传 |
| POST | `/api/videos/upload/init` | 初始化断点续传 |
| PUT | `/api/videos/upload/{sessionId}/chunks/{chunkIndex}` | 上传分片 |
| POST | `/api/videos/upload/{sessionId}/complete` | 完成上传 |
| GET | `/api/videos/upload/sessions` | 可续传会话列表 |
| POST | `/api/videos/import-url/preview` | 链接预览 |
| POST | `/api/videos/import-url` | 链接导入 |
| GET | `/api/videos` | 视频列表 |
| GET | `/api/videos/{id}` | 视频详情 |
| GET | `/api/videos/{id}/transcript` | 转写 |
| GET | `/api/videos/{id}/summary` | 摘要 |
| PUT | `/api/videos/{id}/summary` | 编辑摘要 |
| POST | `/api/videos/{id}/summary/regenerate` | 重新生成摘要 |
| PUT | `/api/videos/{id}/content-type` | 修改内容类型 |
| GET | `/api/videos/{id}/export/markdown` | 导出 Markdown |
| GET | `/api/videos/{id}/stream` | 视频流 |
| POST | `/api/videos/{id}/retry` | 重新处理 |
| POST | `/api/videos/{id}/index/rebuild` | 重建 RAG 索引 |

### 问答

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/videos/{videoId}/chat` | 发送问题 |
| GET | `/api/videos/{videoId}/chat/history` | 最近会话历史 |
| GET | `/api/videos/{videoId}/chat/sessions/{sessionId}/messages` | 指定会话消息 |

### 系统

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/system/import-health` | B 站 Cookie 健康状态 |

---

## 11. 已知限制与后续方向

以下为当前版本的已知边界，不影响核心链路使用：

| 项 | 说明 |
|----|------|
| Web Link 等待时间 | 取决于视频时长与网络，体感差异较大 |
| Cookie 持久化 | 任务级临时文件；失败后重试需重新配置 Cookie |
| Citation 持久化 | 服务端 Redis 仅存文本；刷新后 citation 依赖前端 localStorage 缓存 |
| OSS | 代码已支持，默认关闭，需手动配置 `config/oss.yml` |
| 内容类型 UI | 后端 API 完整；前端详情页切换类型的 UI 待完善 |
| 公网验收 | 部署文档与脚本已就绪，需按 ECS 环境实际跑通 |

---

## 12. 相关文档索引

| 文档 | 路径 |
|------|------|
| 项目 README | [README.md](README.md) |
| 产品路线图 | [docs/PRODUCT_ROADMAP.md](docs/PRODUCT_ROADMAP.md) |
| 版本变更 | [CHANGELOG.md](CHANGELOG.md) |
| Cookie 指南 | [docs/COOKIE.md](docs/COOKIE.md) |
| Docker 部署 | [docs/DOCKER_DEPLOY.md](docs/DOCKER_DEPLOY.md) |
| ECS 裸机部署 | [docs/DEPLOY.md](docs/DEPLOY.md) |
| 实现回溯 | [docs/RETROSPECTIVE.md](docs/RETROSPECTIVE.md) |

---

*本文档基于当前代码库实际实现整理，反映截至 2026-06-17 的产品功能状态。*
