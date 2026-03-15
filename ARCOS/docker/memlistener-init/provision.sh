#!/usr/bin/env bash
# ============================================================================
# MemListener model provisioner — runs as a Docker init container.
#
# Pulls the pre-built quantized model from the Ollama registry.
# Only runs the pull if the model is not already present.
#
# Environment variables (set in docker-compose):
#   OLLAMA_HOST       — Ollama API base URL (default: http://ollama:11434)
#   OLLAMA_MODEL_NAME — model to pull      (default: pierrewagniart/memlistener:q4_k_m)
# ============================================================================
set -euo pipefail

export OLLAMA_HOST="${OLLAMA_HOST:-http://ollama:11434}"
OLLAMA_MODEL_NAME="${OLLAMA_MODEL_NAME:-pierrewagniart/memlistener:q4_k_m}"

echo "=== MemListener Model Provisioner ==="
echo "  Ollama: ${OLLAMA_HOST}"
echo "  Model:  ${OLLAMA_MODEL_NAME}"
echo

# --------------- Wait for Ollama ---------------
echo "[1/3] Waiting for Ollama..."
for i in $(seq 1 60); do
    if ollama list > /dev/null 2>&1; then
        echo "  Ollama is ready."
        break
    fi
    if [ "$i" -eq 60 ]; then
        echo "ERROR: Ollama did not become ready after 60s" >&2
        exit 1
    fi
    sleep 1
done
echo

# --------------- Check if already exists ---------------
echo "[2/3] Checking if '${OLLAMA_MODEL_NAME}' already exists..."
if ollama show "${OLLAMA_MODEL_NAME}" > /dev/null 2>&1; then
    echo "  Model already present. Nothing to do."
    exit 0
fi
echo "  Model not found. Pulling from registry..."
echo

# --------------- Pull ---------------
echo "[3/3] Pulling ${OLLAMA_MODEL_NAME}..."
ollama pull "${OLLAMA_MODEL_NAME}"
echo
echo "=== MemListener provisioning complete ==="
