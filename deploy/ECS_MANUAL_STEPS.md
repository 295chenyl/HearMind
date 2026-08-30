# ECS 部署（120.55.181.0）

GitHub 在阿里云 ECS 上常超时，请从**本机 Windows** 通过 SSH 一键部署。

---

## 方式 A：一键 SSH 部署（推荐）

### 首次：配置免密（只需一次）

本机已有私钥 `C:\Users\33710\.ssh\id_ed25519`，对应公钥：

```text
ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIIKk4Eemz8WSAU5OGxg1R8ZfUmcVb6N95v6z3BlwIXK0 33710@chenyl
```

在项目根目录执行（**会提示输入一次 ECS 密码**，把公钥写入服务器）：

```powershell
cd C:\Users\33710\Desktop\Gen\HearMind
.\deploy\setup-ssh-key.ps1
```

### 日常部署

```powershell
.\deploy\deploy.ps1
```

脚本自动使用 `~\.ssh\id_ed25519`，全程免密、无需再打包上传。

| 参数 | 说明 |
|------|------|
| `-RemoteDir /opt/HearMind` | ECS 上的项目目录（默认） |
| `-SshKey 路径` | 指定私钥，免密部署 |
| `-SkipSecrets` | 不上传 dashscope.key / cookie |
| `-FullRebuild` | 清 Docker 缓存完全重建 |
| `-FreshEnv` | 重新生成 MySQL 密码（**慎用**，已有数据会连不上库） |

**日常改代码后发布：**

```powershell
.\deploy\deploy.ps1
```

**大改 Dockerfile / 构建异常时：**

```powershell
.\deploy\deploy.ps1 -FullRebuild
```

### 可选：配置 SSH 免密（避免多次输入密码）

```powershell
.\deploy\setup-ssh-key.ps1   # 首次，输入一次密码
.\deploy\deploy.ps1          # 之后免密
```

---

## 方式 B：Workbench 手动上传（无 SSH 时备用）

### 1. 本机打包

```powershell
cd C:\Users\33710\Desktop\Gen\HearMind
.\deploy\pack-only.ps1
```

得到：`deploy\hearmind-deploy.tar.gz`（约 0.1 MB）

同时准备 `.env`：运行一次 `.\deploy\upload-to-ecs.ps1` 会在项目根生成 `.env.deploy`（含 MySQL 密码），或手动复制 `.env.example` 为 `.env.deploy` 并填写。

### 2. 上传到 ECS

阿里云控制台 → ECS → **Workbench 远程连接** → 文件上传：

| 本地文件 | ECS 路径 |
|---------|---------|
| `deploy\hearmind-deploy.tar.gz` | `/tmp/hearmind-deploy.tar.gz` |
| `.env.deploy` | `/opt/HearMind/.env` |
| `config\dashscope.key` | `/opt/HearMind/config/dashscope.key` |
| `config\bilibili.cookies.txt` | `/opt/HearMind/config/bilibili.cookies.txt` |

> 路径大小写：`/opt/HearMind` 与 `/opt/hearmind` 均可，下文统一用 `/opt/HearMind`。

### 3. 在 ECS 终端执行

```bash
mkdir -p /opt/HearMind/config
tar -xzf /tmp/hearmind-deploy.tar.gz -C /opt/HearMind
chmod +x /opt/HearMind/deploy/*.sh

# 若未预装 Docker
bash /opt/HearMind/deploy/install-docker-ubuntu.sh

# 启动（首次构建 5-15 分钟）
bash /opt/HearMind/deploy/ecs-docker-up.sh
# 或: cd /opt/HearMind && docker compose up -d --build
```

### 4. 安全组

入方向放行 **TCP 80**（来源 0.0.0.0/0）。

### 5. 访问

http://120.55.181.0

---

## 验收

```bash
cd /opt/HearMind
docker compose ps
docker compose logs -f backend
```

注册 → 登录 → B 站链接导入 → 等待转写完成。

---

## 常用命令

```bash
cd /opt/HearMind
docker compose restart backend
docker compose down
docker compose up -d --build
```

---

## 构建失败：`mvn package` exit code 1

国内 ECS 拉 Maven Central 常超时。新版 Dockerfile 已改用**阿里云 Maven 镜像**与**清华 PyPI 镜像**（`backend/settings-docker.xml`）。

**处理步骤：**

1. 本机重新打包并上传最新 `hearmind-deploy.tar.gz`（见上文「方式 B · 1」）
2. 在 ECS 上覆盖代码并重建：

```bash
cd /opt/HearMind
tar -xzf /tmp/hearmind-deploy.tar.gz -C /opt/HearMind
chmod +x deploy/*.sh
bash deploy/ecs-rebuild.sh
```

3. 若仍失败，查看完整 Maven 日志（不要 `-q`）：

```bash
cd /opt/HearMind
docker compose build --no-cache backend 2>&1 | tee /tmp/backend-build.log
tail -80 /tmp/backend-build.log
```

把日志末尾 `[ERROR]` 行发出来即可定位（常见：JDK 版本、pom 依赖、网络 DNS）。

**若报错 `package com.dovidioai.service.storage does not exist`：**

旧版打包用了 `--exclude=storage`，会把 Java 源码目录 `service/storage/` 一并排除。请用**最新** `pack-only.ps1` 重新打包（包内应有 `VideoStorageFacade.java` 等 4 个文件），再上传重建。
