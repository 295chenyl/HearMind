#!/usr/bin/env bash
# 在 ECS 上启动 HearMind Docker（代码与 .env 已就位）
set -euo pipefail

INSTALL_DIR="${1:-/opt/HearMind}"
cd "$INSTALL_DIR"

if ! command -v docker >/dev/null 2>&1; then
  echo ">>> 安装 Docker..."
  bash deploy/install-docker-ubuntu.sh
fi

chmod +x deploy/*.sh 2>/dev/null || true
bash deploy/ecs-docker-up.sh

PUBLIC_IP=$(curl -s --max-time 3 http://100.100.100.200/latest/meta-data/eip 2>/dev/null || true)
if [[ -z "$PUBLIC_IP" ]]; then
  PUBLIC_IP="<ECS公网IP>"
fi
echo ""
echo "=========================================="
echo "  部署完成: http://${PUBLIC_IP}"
echo "  日志: cd $INSTALL_DIR && docker compose logs -f backend"
echo "=========================================="
