#!/usr/bin/env bash
# ============================================================================
# build-memlistener.sh — Local dev helper
#
# Provisions the MemListener model into a running Ollama instance.
# For Docker deployments, use `docker compose up memlistener-init` instead.
#
# Usage:
#   ./scripts/build-memlistener.sh              # default: Q4_K_M
#   ./scripts/build-memlistener.sh q5_k_m       # override quantization
# ============================================================================
set -euo pipefail

OLLAMA_HOST="${OLLAMA_HOST:-http://localhost:11434}"
HF_REPO="Robot2050/MemListener"
HF_SUBFOLDER="dapo_qwen3_8B_mem"
QUANT="${1:-q4_k_m}"
MODEL_NAME="memlistener:${QUANT}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WORK_DIR="${SCRIPT_DIR}/../models/memlistener-build"

# --------------- Pre-flight ---------------
for cmd in ollama huggingface-cli; do
    if ! command -v "$cmd" &>/dev/null; then
        echo "ERROR: '$cmd' not found." >&2
        exit 1
    fi
done

if ! curl -sf "${OLLAMA_HOST}/api/tags" > /dev/null 2>&1; then
    echo "ERROR: Ollama not reachable at ${OLLAMA_HOST}. Start it first." >&2
    exit 1
fi

# --------------- Check if already exists ---------------
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "${OLLAMA_HOST}/api/show" \
    -H "Content-Type: application/json" \
    -d "{\"name\": \"${MODEL_NAME}\"}")

if [ "$STATUS" = "200" ]; then
    echo "Model '${MODEL_NAME}' already exists in Ollama. Nothing to do."
    exit 0
fi

echo "=== Provisioning ${MODEL_NAME} ==="
echo "  Source: ${HF_REPO}/${HF_SUBFOLDER}"
echo "  Quant:  ${QUANT}"
echo

# --------------- Download ---------------
echo "[1/3] Downloading from HuggingFace..."
mkdir -p "${WORK_DIR}"
huggingface-cli download "${HF_REPO}" \
    --include "${HF_SUBFOLDER}/*" \
    --local-dir "${WORK_DIR}" \
    --local-dir-use-symlinks False
echo

# --------------- Create via Ollama ---------------
echo "[2/3] Creating quantized model (this takes several minutes)..."
MODELFILE_PATH="${WORK_DIR}/Modelfile"
cat > "${MODELFILE_PATH}" <<EOF
FROM ${WORK_DIR}/${HF_SUBFOLDER}
QUANTIZE ${QUANT}
EOF

ollama create "${MODEL_NAME}" -f "${MODELFILE_PATH}"
echo

# --------------- Cleanup ---------------
echo "[3/3] Cleaning up source files..."
rm -rf "${WORK_DIR}"

echo "=== Done: ${MODEL_NAME} is ready ==="
