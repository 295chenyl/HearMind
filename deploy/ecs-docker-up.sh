#!/usr/bin/env bash
# HearMind — 阿里云 ECS Docker 一键启动
# 在项目根目录执行：bash deploy/ecs-docker-up.sh

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if ! command -v docker >/dev/null 2>&1; then
  echo "错误: 未安装 Docker，请先执行: bash deploy/install-docker-ubuntu.sh"
  exit 1
fi

COMPOSE="docker compose"
if ! docker compose version >/dev/null 2>&1; then
  COMPOSE="docker-compose"
fi

if [[ ! -f .env ]]; then
  cp .env.example .env
  echo "已创建 .env，请编辑 MYSQL_ROOT_PASSWORD、AUTH_JWT_SECRET、DASHSCOPE_API_KEY 后重新运行"
  exit 1
fi

if [[ ! -f config/dashscope.key ]] && ! grep -q '^DASHSCOPE_API_KEY=sk-' .env 2>/dev/null; then
  echo "警告: 未配置 DashScope Key。请创建 config/dashscope.key 或在 .env 中设置 DASHSCOPE_API_KEY"
fi

if [[ ! -f config/bilibili.cookies.txt ]]; then
  echo "警告: 未找到 config/bilibili.cookies.txt，B 站 Web Link 导入可能失败（见 docs/COOKIE.md）"
fi

mkdir -p config

echo ">>> 构建并启动容器..."
$COMPOSE up -d --build

echo ""
echo ">>> 容器状态"
$COMPOSE ps

echo ""
echo ">>> 部署完成"
echo "访问: http://<ECS公网IP>:${HTTP_PORT:-80}"
echo "查看日志: $COMPOSE logs -f backend"
echo "停止服务: $COMPOSE down"
