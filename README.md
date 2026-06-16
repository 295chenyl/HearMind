# 听悟 HearMind

> **听懂每一帧，悟透每一个问题。**

音视频智能理解平台：本地上传或链接导入 → AI 转写 → 智能摘要 → 基于内容的知识增强问答。可自部署的开源学习工具。

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## 文档

| 文档 | 说明 |
|------|------|
| [产品路线图](docs/PRODUCT_ROADMAP.md) | 需求、Phase 排期、验收标准 |
| [实现回溯](docs/RETROSPECTIVE.md) | Plan vs 实现偏差与修复记录 |
| [CHANGELOG](CHANGELOG.md) | 版本迭代记录 |
| [Cookie 指南](docs/COOKIE.md) | B 站链接导入 Cookie 获取 |
| [部署指南](docs/DEPLOY.md) | ECS 上线（P5） |

## 技术栈

- 前端：Vue 3 + Vite + Element Plus
- 后端：Spring Boot 3 + MySQL
- 视频 URL：yt-dlp + ffmpeg
- ASR：DashScope Paraformer
- LLM：通义千问（DashScope）

---

## 一、Java 17 与环境变量

本机已通过 winget 安装 **Microsoft OpenJDK 17**，并配置了用户级环境变量：

| 变量 | 值 |
|------|-----|
| `JAVA_HOME` | `C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot` |
| `Path` | 已 prepend `%JAVA_HOME%\bin` |

**验证（需新开终端）：**

```powershell
java -version
# 应显示 openjdk version "17.x"
```

若仍显示 Java 8，请 **关闭并重新打开** PowerShell / Cursor 终端，使环境变量生效。

手动修改方式：Windows「设置 → 系统 → 关于 → 高级系统设置 → 环境变量」，将 `JAVA_HOME` 指向上面的 JDK 17 路径，并在用户 `Path` 最前面加入 `%JAVA_HOME%\bin`。

---

## 二、DashScope API Key 配置（文件方式）

**推荐**：直接编辑项目根目录下的 Key 文件（已加入 `.gitignore`，不会提交到 Git）：

```
config/dashscope.key
```

文件内容示例（只保留一行 Key，不要引号）：

```
sk-你的真实Key
```

以 `#` 开头的行会被忽略。也可参考模板 `config/dashscope.key.example`。

**优先级（从高到低）：**

1. 环境变量 `DASHSCOPE_API_KEY`
2. `application.yml` 中的 `dashscope.api-key`
3. 文件 `config/dashscope.key`（默认路径，可在 yml 中改 `dashscope.key-file`）

修改 Key 文件后，**重启后端**即可生效。

---

## 三、yt-dlp + ffmpeg 安装与使用

### 3.1 本机已安装（winget）

| 工具 | 用途 | 验证命令 |
|------|------|----------|
| **ffmpeg** | 从视频提取音频、读取时长 | `ffmpeg -version` |
| **ffprobe** | 检测视频时长（随 ffmpeg 一起） | `ffprobe -version` |
| **yt-dlp** | 下载 B站 / YouTube 等平台视频 | `yt-dlp --version` |

安装后需 **新开终端** 再执行上述命令。

### 3.2 在本项目里怎么用

你 **不需要手动敲 yt-dlp / ffmpeg 命令**。后端在处理视频时会自动调用：

```
用户粘贴 URL 或上传文件
        ↓
【URL 且非直链】yt-dlp 下载视频到 storage/videos/{id}/
        ↓
【直链 URL】HTTP 直接下载
        ↓
ffprobe 检查时长（≤50 分钟）
        ↓
ffmpeg 从视频提取 audio.mp3
        ↓
音频上传 DashScope → Paraformer 转写 → 通义千问摘要
```

对应配置在 `application.yml`：

```yaml
app:
  ytdlp-path: yt-dlp      # 若命令找不到，可改为绝对路径
  ffmpeg-path: ffmpeg
  ffprobe-path: ffprobe
```

### 3.3 手动测试（可选）

```powershell
# 查看 B站视频信息（不下载）
yt-dlp --dump-single-json "https://www.bilibili.com/video/BVxxxx"

# 手动下载测试
yt-dlp -o "test.%(ext)s" "https://www.bilibili.com/video/BVxxxx"

# 手动从视频抽音频
ffmpeg -i test.mp4 -vn -acodec libmp3lame -q:a 2 audio.mp3
```

### 3.4 前端如何使用

1. 打开 http://localhost:5173
2. **本地上传**：选文件 →「开始上传并处理」
3. **URL 导入**：粘贴 B站 / YouTube / mp4 直链 →「导入并处理」
4. 进入详情页等待状态变为「已完成」，即可看转写、摘要并提问

---

## 四、快速启动

### 1. 启动 MySQL

```powershell
cd <项目根目录>
docker compose up -d
```

### 2. 填写 API Key

编辑 `config/dashscope.key`，写入你的 `sk-...`。

### 3. 启动后端

```powershell
cd backend
mvn spring-boot:run
```

默认端口：`8080`（请在 **项目根目录** 或确保 `config/dashscope.key` 相对路径正确；从 `backend` 目录启动时 Key 文件路径为 `../config/dashscope.key`，可在 yml 中调整）。

> **建议**：从项目根目录启动，或把 `dashscope.key-file` 改为绝对路径。

### 4. 启动前端（需 Node.js 18+）

```powershell
cd frontend
npm install
npm run dev
```

访问：http://localhost:5173

---

## 五、配置项

`backend/src/main/resources/application.yml`：

| 配置 | 说明 |
|------|------|
| `spring.datasource.*` | MySQL 连接 |
| `app.storage-path` | 本地视频存储（默认 `./storage`） |
| `app.max-duration-seconds` | 最大 50 分钟（3000 秒） |
| `dashscope.key-file` | API Key 文件路径（默认 `./config/dashscope.key`） |
| `dashscope.llm-model` | 默认 `qwen-plus` |
| `dashscope.asr-model` | 默认 `paraformer-v2` |

---

## 六、常见问题

**Q: 后端找不到 API Key**  
A: 确认 `config/dashscope.key` 存在且有一行有效的 `sk-...`，然后重启后端。

**Q: URL 导入失败**  
A: 见 [docs/COOKIE.md](docs/COOKIE.md)。B 站可在导入页上传 Cookie；或配置 `config/bilibili.cookies.txt` 作为 fallback。

**Q: 从 backend 目录启动读不到 Key 文件**  
A: 在 `application.yml` 设置：`dashscope.key-file: ../config/dashscope.key`

**Q: 转写较慢**  
A: Paraformer 为异步任务，长视频可能需数分钟，详情页会自动轮询。
