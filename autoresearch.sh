#!/usr/bin/env bash
# autoresearch.sh — runs the EouLatencyBench and prints METRIC_EOU_MS=<n>.
# Called by the autoresearch loop after each candidate edit.
#
# Exit codes:
#   0  — benchmark ran successfully and emitted METRIC_EOU_MS
#   1  — build or runtime failure (treat as "discard candidate")
#   2  — environment failure (faster-whisper not reachable)
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$REPO_ROOT"

LOG="${REPO_ROOT}/autoresearch.last.log"
: > "$LOG"

echo "[autoresearch] ensuring faster-whisper container is up..." | tee -a "$LOG"
if ! curl -fsS --max-time 3 http://localhost:8000/health >/dev/null 2>&1; then
    (cd ARCOS && docker compose up -d faster-whisper) 2>&1 | tee -a "$LOG"
    # Wait up to 180s for healthy
    for i in {1..60}; do
        if curl -fsS --max-time 3 http://localhost:8000/health >/dev/null 2>&1; then
            echo "[autoresearch] faster-whisper healthy" | tee -a "$LOG"
            break
        fi
        sleep 3
    done
    if ! curl -fsS --max-time 3 http://localhost:8000/health >/dev/null 2>&1; then
        echo "[autoresearch] FATAL: faster-whisper never became healthy" | tee -a "$LOG"
        exit 2
    fi
fi

echo "[autoresearch] running EouLatencyBench..." | tee -a "$LOG"
START_TS=$(date +%s)
cd ARCOS
ARCOS_BENCH=1 mvn -q test -Dtest=EouLatencyBench -DfailIfNoTests=false \
    -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tee -a "$LOG"
RC=${PIPESTATUS[0]}
cd ..
END_TS=$(date +%s)
ELAPSED=$((END_TS - START_TS))

if [[ $RC -ne 0 ]]; then
    echo "[autoresearch] bench FAILED (rc=$RC, elapsed=${ELAPSED}s)" | tee -a "$LOG"
    # Surface metric line if it managed to print before failing
    grep -E "^METRIC_EOU_MS=" "$LOG" | tail -1
    exit 1
fi

METRIC=$(grep -E "^METRIC_EOU_MS=" "$LOG" | tail -1)
if [[ -z "$METRIC" ]]; then
    echo "[autoresearch] FATAL: bench produced no METRIC_EOU_MS line" | tee -a "$LOG"
    exit 1
fi

echo "[autoresearch] elapsed=${ELAPSED}s"
echo "$METRIC"
grep -E "^METRIC_EOU_(MIN|MAX|MEAN)_MS=" "$LOG" | tail -3
