#!/usr/bin/env bash
# ============================================================================
# build-memlistener.sh — Pull the MemListener model into local Ollama.
#
# For Docker deployments, use `docker compose up memlistener-init` instead.
#
# Usage:
#   ./scripts/build-memlistener.sh
# ============================================================================
set -euo pipefail

OLLAMA_HOST="${OLLAMA_HOST:-http://localhost:11434}"
MODEL_NAME="pierrewagniart/memlistener:q4_k_m"

if ! command -v ollama &>/dev/null; then
    echo "ERROR: 'ollama' not found. Install it from https://ollama.com" >&2
    exit 1
fi

if ! curl -sf "${OLLAMA_HOST}/api/tags" > /dev/null 2>&1; then
    echo "ERROR: Ollama not reachable at ${OLLAMA_HOST}. Start it first." >&2
    exit 1
fi

STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "${OLLAMA_HOST}/api/show" \
    -H "Content-Type: application/json" \
    -d "{\"name\": \"${MODEL_NAME}\"}")

if [ "$STATUS" = "200" ]; then
    echo "Model '${MODEL_NAME}' already present. Nothing to do."
    exit 0
fi

echo "Pulling ${MODEL_NAME}..."
export OLLAMA_HOST
ollama pull "${MODEL_NAME}"
echo "Done."
