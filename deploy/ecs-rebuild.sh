#!/usr/bin/env bash
# 在 ECS 上修复构建失败后重新部署（/opt/HearMind 或 /opt/hearmind）
set -euo pipefail
DIR="${1:-/opt/HearMind}"
cd "$DIR"

echo ">>> 清理旧构建缓存..."
docker compose down 2>/dev/null || true
docker builder prune -f 2>/dev/null || true

echo ">>> 重新构建并启动（使用阿里云 Maven/npm 镜像，约 5-15 分钟）..."
docker compose build --no-cache backend frontend
docker compose up -d

echo ""
docker compose ps
echo ""
echo "访问: http://120.55.181.0"
echo "日志: docker compose logs -f backend"
