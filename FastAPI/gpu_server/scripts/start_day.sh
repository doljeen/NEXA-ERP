#!/usr/bin/env bash
# 하루 시작 (서빙 모드): vllm, ocr, mlflow 세션을 tmux로 띄운다. 이미 떠 있는 세션은 건너뜀.
# GPU 서버 규칙(데몬·cron 금지, 24시간마다 재시작)에 맞춰 매일 아침 직접 실행하고, 저녁에 stop_day.sh로 내린다.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

start() {   # 사용법: start <세션 이름> <스크립트 파일>
  if tmux has-session -t "$1" 2>/dev/null; then
    echo "[$1] 이미 실행 중"
  else
    tmux new-session -d -s "$1" "bash $SCRIPT_DIR/$2"
    echo "[$1] 시작"
  fi
}

# Tailscale(ts)은 로그인 상태를 유지해야 해서 따로 관리 → 없으면 알려만 줌
if ! tmux has-session -t ts 2>/dev/null; then
  echo "[ts] Tailscale 세션이 없습니다. 먼저 켜야 PC·EC2에서 접근할 수 있어요"
fi

start vllm serve.sh
start ocr serve_ocr.sh
start mlflow serve_mlflow.sh

echo "세션 확인: tmux ls"
echo "로그 확인: tail -f ~/ai/logs/*-$(date +%F).log"