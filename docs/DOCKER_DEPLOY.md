# Docker 部署指南（阿里云 ECS）

> 先跑通，再反学 Docker。本方案用 `docker compose` 一键启动 MySQL、Redis、后端、前端（Nginx）。

## 架构

```text
浏览器 :80
    ↓
frontend (Nginx + Vue 静态文件)
    ↓ /api
backend (Spring Boot + ffmpeg + yt-dlp)
    ↓
mysql:3306    redis:6379
    ↓
挂载 ./config（只读）  +  volume hearmind_storage（视频文件）
```

| 容器 | 作用 |
|------|------|
| `hearmind-frontend` | 对外 80 端口，反代 `/api` |
| `hearmind-backend` | Java 后端，处理转写/摘要 |
| `hearmind-mysql` | 数据库（Flyway 自动建表） |
| `hearmind-redis` | 限流 + 问答会话 |

**不需要**单独购买 OSS；DashScope ASR 上传走 API，不占用你的 OSS Bucket。

---

## 一、ECS 准备

1. **规格**：2 vCPU / 4 GiB 起（与你当前选型一致）
2. **系统**：Ubuntu 22.04 64 位
3. **预装应用**：
   - 已选「Docker」→ 可跳过安装 Docker 步骤
   - 未选 → 用下面脚本安装
4. **安全组**：入方向放行 **22（SSH）**、**80（HTTP）**
5. **不要**对公网开放 3306 / 6379 / 8080

---

## 二、登录 ECS

```bash
ssh root@120.55.181.0
```

**GitHub 在国内 ECS 上常超时**，不要依赖 `git clone https://github.com/...`。

### 推荐：从本机 Windows 一键上传部署

在本机项目根目录 PowerShell 执行（需已能 SSH 登录 ECS）：

```powershell
.\deploy\deploy.ps1
# 或
.\deploy\upload-to-ecs.ps1 -EcsIp 120.55.181.0
```

脚本会：打包代码 → scp 上传 → 上传 `config/dashscope.key` 与 `bilibili.cookies.txt` → 远程 `docker compose up --build`。

### 或：手动安装 Docker（若未预装）

若使用非 root 用户，安装后加入 docker 组并重新登录：

```bash
sudo usermod -aG docker $USER
```

---

## 三、上传配置（本机已有 config 时）

在**本地电脑**执行（把 `<ECS公网IP>` 换成实际 IP）：

```bash
scp -r config/dashscope.key config/bilibili.cookies.txt root@<ECS公网IP>:/opt/hearmind/config/
```

或在 ECS 上直接创建：

```bash
mkdir -p /opt/hearmind/config
nano /opt/hearmind/config/dashscope.key    # 一行 sk- 开头 Key
# B 站 Cookie 见 docs/COOKIE.md
nano /opt/hearmind/config/bilibili.cookies.txt
```

---

## 四、配置环境变量并启动

```bash
cd /opt/hearmind

cp .env.example .env
nano .env
```

至少修改：

```env
MYSQL_ROOT_PASSWORD=你的强密码
AUTH_JWT_SECRET=至少32字符的随机字符串
DASHSCOPE_API_KEY=sk-你的Key
HTTP_PORT=80
```

一键启动：

```bash
bash deploy/ecs-docker-up.sh
```

或手动：

```bash
docker compose up -d --build
docker compose ps
docker compose logs -f backend
```

首次构建约 **5–15 分钟**（Maven 打包 + 前端 build）。

---

## 五、验收

浏览器访问：`http://<ECS公网IP>`

1. 注册 / 登录
2. 链接导入 B 站视频（需 `config/bilibili.cookies.txt`）
3. 等待处理完成 → 转写 / 摘要 / 问答

常用命令：

```bash
docker compose ps              # 状态
docker compose logs -f backend # 后端日志
docker compose restart backend # 重启后端
docker compose down            # 停止（数据在 volume 里保留）
docker compose down -v         # 停止并删除数据卷（慎用）
```

---

## 六、反学 Docker：对照本项目的文件

| 文件 | 学什么 |
|------|--------|
| `docker-compose.yml` | 多容器编排、依赖顺序、环境变量、volume |
| `backend/Dockerfile` | 多阶段构建（Maven 编译 + 精简运行镜像） |
| `frontend/Dockerfile` | Node 构建 + Nginx 托管静态资源 |
| `deploy/nginx-docker.conf` | 容器内反代 `backend:8080` |
| `application-docker.yml` | 容器内 MySQL/Redis 主机名 |
| `.env` | 密钥与端口，不提交 Git |

动手实验建议：

```bash
# 进入后端容器
docker exec -it hearmind-backend bash
yt-dlp --version
ffmpeg -version

# 只看某服务日志
docker compose logs mysql --tail=50

# 改代码后重新构建
docker compose up -d --build backend
```

---

## 七、常见问题

| 现象 | 处理 |
|------|------|
| 80 端口无法访问 | 检查安全组、`.env` 中 `HTTP_PORT`、`docker compose ps` |
| 后端启动失败 / 连不上 MySQL | `docker compose logs mysql backend`，确认 MySQL healthy |
| B 站 412 | 更新 `config/bilibili.cookies.txt` 后 `docker compose restart backend` |
| DashScope 报错 | 检查 `DASHSCOPE_API_KEY` 或 `config/dashscope.key` |
| 磁盘不足 | 视频在 volume `hearmind_storage`，可 `docker volume inspect hearmind_hearmind_storage` |

---

## 八、与 systemd 部署的关系

- **Docker 部署**：本文档，适合学习容器化、快速复现环境
- **裸机部署**：见 [DEPLOY.md](DEPLOY.md)，适合长期单机运维

两者可并存于不同环境，配置目录结构相同（`config/`）。
