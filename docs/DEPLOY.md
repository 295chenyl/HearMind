# ECS 部署指南

本文档描述在阿里云 ECS 上部署 dovideoAi pro（v1 本地存储，OSS 关闭）。

## 架构

```text
Browser → Nginx:80/443 → /        frontend/dist
                      → /api      Spring Boot :8080
                      → MySQL + Redis + DashScope
                      → backend/storage 本地盘
```

## 1. 准备 ECS

- 系统：Ubuntu 22.04 LTS 或 Alibaba Cloud Linux 3
- 规格：2 vCPU / 4 GiB 起（含 ASR/LLM 调用，网络需稳定）
- 安全组：开放 22（SSH）、80、443；**不要**对公网开放 3306/6379/8080

## 2. 安装依赖

```bash
sudo apt update
sudo apt install -y openjdk-17-jdk nginx mysql-server redis-server ffmpeg
pip install yt-dlp
```

## 3. 数据库

```bash
sudo mysql -e "CREATE DATABASE dovidioai CHARACTER SET utf8mb4;"
sudo mysql -e "CREATE USER 'dovidioai'@'localhost' IDENTIFIED BY 'change-me';"
sudo mysql -e "GRANT ALL ON dovidioai.* TO 'dovidioai'@'localhost';"
```

## 4. 部署代码

```bash
sudo useradd -r -m -d /opt/dovidioai dovidioai || true
sudo mkdir -p /opt/dovidioai
sudo chown -R $USER:dovidioai /opt/dovidioai

git clone https://github.com/295chenyl/doVideoAi.git /opt/dovidioai/src
cd /opt/dovidioai/src/backend && mvn -DskipTests package
cd /opt/dovidioai/src/frontend && npm ci && npm run build

sudo cp /opt/dovidioai/src/backend/target/dovidioai-backend-*.jar /opt/dovidioai/backend/dovidioai-backend.jar
sudo cp -r /opt/dovidioai/src/frontend/dist /opt/dovidioai/frontend/
sudo mkdir -p /opt/dovidioai/backend/storage
sudo chown -R dovidioai:dovidioai /opt/dovidioai
```

## 5. 配置

在 `/opt/dovidioai/backend/` 创建 `config/dashscope.key`（一行 `sk-` Key）。

编辑 `deploy/dovidioai.service` 中的环境变量后安装：

```bash
sudo cp deploy/dovidioai.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now dovidioai
```

安装 Nginx：

```bash
sudo cp deploy/nginx.conf /etc/nginx/sites-available/dovidioai
sudo ln -sf /etc/nginx/sites-available/dovidioai /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
```

## 6. HTTPS（可选）

使用 certbot 或阿里云证书，在 Nginx 中增加 `listen 443 ssl` 与证书路径。

## 7. 验收清单

公网访问站点，完成：

1. 注册 / 登录
2. B 站链接导入（用户上传 Cookie）
3. 转写完成，时间戳可点击跳转
4. 摘要编辑 / 按场景 regenerate
5. RAG 问答含引用
6. 导出 Markdown
7. `sudo reboot` 后服务自启

## 8. 运维

| 命令 | 说明 |
|------|------|
| `sudo systemctl status dovidioai` | 后端状态 |
| `sudo journalctl -u dovidioai -f` | 后端日志 |
| `sudo systemctl restart dovidioai` | 重启后端 |

存储目录：`/opt/dovidioai/backend/storage`（定期备份）。
