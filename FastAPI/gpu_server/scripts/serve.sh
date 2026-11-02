#!/usr/bin/env bash
# vLLM serving (daytime). Run inside tmux on the GPU server.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
set -a
source "$SCRIPT_DIR/../.env"
set +a

source "$HOME/ai/.venv/bin/activate"
mkdir -p "$HOME/ai/logs"

CUDA_VISIBLE_DEVICES="$GPU_ID" vllm serve "$MODEL_PATH" \
  --served-model-name "$SERVED_MODEL_NAME" \
  --host 0.0.0.0 \
  --port "$PORT" \
  --api-key "$VLLM_API_KEY" \
  --gpu-memory-utilization "$GPU_MEM_UTIL" \
  --max-model-len "$MAX_MODEL_LEN" \
  --max-num-seqs "$MAX_NUM_SEQS" \
  --limit-mm-per-prompt '{"image": 4, "video": 0}' \
  --reasoning-parser qwen3 \
  2>&1 | tee -a "$HOME/ai/logs/vllm-$(date +%F).log"