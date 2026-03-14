#!/usr/bin/env bash
# ============================================================================
# MemListener model provisioner — runs as a Docker init container.
#
# Downloads the model from HuggingFace into a shared staging volume
# (/staging), then tells Ollama to create a quantized model from it.
# Both this container and the Ollama container mount the same volume,
# so the FROM path in the Modelfile resolves inside Ollama's filesystem.
#
# Environment variables (set in docker-compose):
#   OLLAMA_HOST       — Ollama API base URL (default: http://ollama:11434)
#   HF_REPO           — HuggingFace repo   (default: Robot2050/MemListener)
#   HF_SUBFOLDER      — model subfolder    (default: dapo_qwen3_8B_mem)
#   OLLAMA_MODEL_NAME — target model name  (default: memlistener:q4_k_m)
#   QUANTIZATION      — quantization level (default: q4_k_m)
# ============================================================================
set -euo pipefail

OLLAMA_HOST="${OLLAMA_HOST:-http://ollama:11434}"
HF_REPO="${HF_REPO:-Robot2050/MemListener}"
HF_SUBFOLDER="${HF_SUBFOLDER:-dapo_qwen3_8B_mem}"
OLLAMA_MODEL_NAME="${OLLAMA_MODEL_NAME:-memlistener:q4_k_m}"
QUANTIZATION="${QUANTIZATION:-q4_k_m}"

# Shared volume mounted in both this container and the Ollama container
STAGING_DIR="/staging"
MODEL_DIR="${STAGING_DIR}/${HF_SUBFOLDER}"

echo "=== MemListener Model Provisioner ==="
echo "  Ollama:  ${OLLAMA_HOST}"
echo "  Model:   ${OLLAMA_MODEL_NAME}"
echo "  Source:  ${HF_REPO}/${HF_SUBFOLDER}"
echo "  Quant:   ${QUANTIZATION}"
echo

# --------------- Wait for Ollama to be ready ---------------
echo "[1/5] Waiting for Ollama..."
for i in $(seq 1 60); do
    if curl -sf "${OLLAMA_HOST}/api/tags" > /dev/null 2>&1; then
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

# --------------- Check if model already exists ---------------
echo "[2/5] Checking if '${OLLAMA_MODEL_NAME}' already exists..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "${OLLAMA_HOST}/api/show" \
    -H "Content-Type: application/json" \
    -d "{\"name\": \"${OLLAMA_MODEL_NAME}\"}")

if [ "$STATUS" = "200" ]; then
    echo "  Model already exists. Nothing to do."
    exit 0
fi
echo "  Model not found (HTTP ${STATUS}). Proceeding with provisioning."
echo

# --------------- Download from HuggingFace ---------------
echo "[3/5] Downloading ${HF_REPO}/${HF_SUBFOLDER} to shared staging volume..."
mkdir -p "${STAGING_DIR}"

huggingface-cli download "${HF_REPO}" \
    --include "${HF_SUBFOLDER}/*" \
    --local-dir "${STAGING_DIR}" \
    --local-dir-use-symlinks False \
    --quiet

echo "  Downloaded to ${MODEL_DIR}"
ls -lh "${MODEL_DIR}"/*.safetensors 2>/dev/null | head -5
echo

# --------------- Create model via Ollama API ---------------
echo "[4/5] Creating quantized model via Ollama API..."
echo "  Ollama will read safetensors from ${MODEL_DIR} (shared volume)."
echo "  Converting to GGUF + quantizing to ${QUANTIZATION}. This takes several minutes..."

# Build the Modelfile content — only FROM + QUANTIZE.
# Runtime parameters (temperature, num_predict) are set by the Java client.
MODELFILE_CONTENT="FROM ${MODEL_DIR}
QUANTIZE ${QUANTIZATION}"

# Build JSON payload with python to avoid shell escaping issues
JSON_PAYLOAD=$(python3 -c "
import json, sys
payload = {
    'name': sys.argv[1],
    'modelfile': sys.argv[2]
}
print(json.dumps(payload))
" "${OLLAMA_MODEL_NAME}" "${MODELFILE_CONTENT}")

# Call /api/create (streaming NDJSON response)
# Track success via a temp file since the while loop runs in a subshell
RESULT_FILE=$(mktemp)
echo "pending" > "${RESULT_FILE}"

curl -s --no-buffer -X POST "${OLLAMA_HOST}/api/create" \
    -H "Content-Type: application/json" \
    -d "${JSON_PAYLOAD}" \
    | while IFS= read -r line; do
        # Extract status and error fields
        status=$(echo "$line" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))" 2>/dev/null || true)
        error=$(echo "$line" | python3 -c "import sys,json; print(json.load(sys.stdin).get('error',''))" 2>/dev/null || true)

        if [ -n "$error" ]; then
            echo "  ERROR: $error" >&2
            echo "error" > "${RESULT_FILE}"
        elif [ -n "$status" ]; then
            echo "  $status"
            echo "success" > "${RESULT_FILE}"
        fi
    done

RESULT=$(cat "${RESULT_FILE}")
rm -f "${RESULT_FILE}"

if [ "$RESULT" = "error" ]; then
    echo "ERROR: Model creation failed. Keeping staging files for debugging." >&2
    exit 1
fi

# Verify the model was actually created
VERIFY_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "${OLLAMA_HOST}/api/show" \
    -H "Content-Type: application/json" \
    -d "{\"name\": \"${OLLAMA_MODEL_NAME}\"}")

if [ "$VERIFY_STATUS" != "200" ]; then
    echo "ERROR: Model creation appeared to succeed but model not found in Ollama." >&2
    exit 1
fi

echo
echo "  Model '${OLLAMA_MODEL_NAME}' created and verified."
echo

# --------------- Cleanup staging ---------------
echo "[5/5] Cleaning up staging files..."
rm -rf "${MODEL_DIR}"
echo "  Done."
echo
echo "=== MemListener provisioning complete ==="
