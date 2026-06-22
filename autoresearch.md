# Autoresearch session — Audio IO end-of-utterance latency

| Field | Value |
|---|---|
| **Metric** | `EOU_LATENCY_MS` — wall-clock from last non-silent audio frame to STT result returned |
| **Direction** | lower is better |
| **Unit** | ms (median of 10 measured runs, 3 warmup) |
| **Branch** | `optim/audio-io-latency` |
| **Benchmark** | `./autoresearch.sh` (calls `mvn test -Dtest=EouLatencyBench` with `ARCOS_BENCH=1`) |
| **Fixture** | `ARCOS/src/test/resources/audio/eou_fixture.wav` — 3.0s band-limited noise + 2.5s silence @ 44.1kHz mono 16-bit |
| **STT backend** | faster-whisper Docker @ `http://localhost:8000`, model `deepdml/faster-whisper-large-v3-turbo-ct2`, lang `fr` |
| **Max iterations** | 15 |
| **Stop conditions** | 15 iters reached, OR EOU ≤ 400 ms, OR 3 consecutive iters with no improvement ≥ 2% |

## Files in scope

- `ARCOS/src/main/java/org/arcos/IO/InputHandling/**`
- `ARCOS/src/main/java/org/arcos/Producers/WakeWordProducer.java`
- `ARCOS/src/main/java/org/arcos/Configuration/AudioProperties.java`
- `ARCOS/src/main/resources/application.properties`
- `ARCOS/src/test/java/org/arcos/Benchmarks/EouLatencyBench.java` (harness; can be edited but does not count as a "real" code change)
- `ARCOS/src/test/resources/audio/eou_fixture.wav`

## Baseline known issues (pre-optimization)

1. `silenceDurationMs = 1200` — fixed wait, dominates EOU.
2. `isSilence` uses a fixed RMS threshold against `silenceThreshold = 1000`, no adaptive noise floor.
3. STT is invoked synchronously after the full silence window elapses — no overlap with the wait.
4. `OkHttpClient` is recreated per `SttGate` instance — no connection keep-alive across utterances.
5. WAV is built and POSTed as one blob; faster-whisper supports streaming endpoints we do not use.
6. 44.1kHz → 16kHz resampling runs a 21-tap FIR per frame on every sample (CPU; minor but visible).

## Dashboard

| Iter | Status | EOU median (ms) | Δ vs prev | Δ vs baseline | Notes |
|---:|:--|---:|---:|---:|:--|
| 0 (baseline) | kept | **6800** | — | — | shipped defaults; min=6679 max=6926 mean=6797 |
| 1 | kept | **6008** | -792 (-11.6%) | -792 (-11.6%) | silence-duration 1200→600, conversation 1500→800 |

## Baseline decomposition (per timestamps in run log)

| Component | Time | Notes |
|---|---:|---|
| Silence detection wait | ~1150 ms | `silenceDurationMs = 1200` minus speech→silence frame-boundary jitter |
| STT HTTP round-trip (faster-whisper-large-v3-turbo CPU int8 on ~4.2s audio) | ~5650 ms | `WHISPER__COMPUTE_TYPE=int8`, ~1.34× real-time |
| **Total EOU** | **~6800 ms** | |

Main takeaway: **the STT model dominates, not the silence wait.** Cheap wins on `silenceDurationMs` can save ~1s at best. Crossing under 1s wall-clock requires changing the STT backend, the model size, or finding a way to start STT speculatively before the silence window elapses.

## Revised target & stop conditions

- **Stretch target:** EOU ≤ 1000 ms (6.8× speedup).
- **Acceptance target:** EOU ≤ 2000 ms (3.4× speedup).
- **Stop after:** 15 iterations OR 3 consecutive iterations with no improvement ≥ 2%.

## Iteration log

Detailed entries appended per iteration in `autoresearch.jsonl`. This file is the human-readable summary.

### Iteration 0 — baseline

- **Edits:** none (shipped defaults: `silenceDurationMs=1200`, `silenceThreshold=1000`, `arcos.stt.backend=FASTER_WHISPER`, model `deepdml/faster-whisper-large-v3-turbo-ct2`).
- **EOU median:** 6800 ms (n=10, min=6679, max=6926).
- **Decision:** keep as baseline reference.

### Iteration 1 — trim silence wait

- **Hypothesis:** `silenceDurationMs` is a hardcoded wait; halving it should save ~600 ms with no accuracy impact (human within-sentence pauses are typically <300 ms).
- **Edits:**
  - `application.properties`: `arcos.audio.silence-duration-ms` 1200 → 600
  - `application.properties`: `arcos.audio.conversation-silence-ms` 1500 → 800 (multi-turn analog, kept consistent)
  - `EouLatencyBench.baselineAudioProps`: mirrored
- **EOU median:** 6008 ms (n=10, min=5908, max=6293, mean=6035, σ≈95)
- **Δ vs prev:** −0.792 s (−11.6%)
- **Decision:** **keep**. Result slightly better than predicted (warmer STT cache likely).
- **Note:** STT inference now ~5.4 s of total — dominant cost is firmly the STT model, not the silence wait.
