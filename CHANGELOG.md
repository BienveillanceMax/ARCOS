# CHANGELOG — ARCOS

Lab notebook for substantial work. Newest entries first.

## 2026-06-22 — Start autoresearch loop: audio-IO end-of-utterance latency

- **Branch:** `optim/audio-io-latency` (off `main`, 14 commits ahead of origin).
- **Goal:** minimize wall-clock `EOU_LATENCY_MS` = (silence-detection wait + STT round-trip).
- **Harness:** new test `ARCOS/src/test/java/org/arcos/Benchmarks/EouLatencyBench.java`, gated by `ARCOS_BENCH=1`. Replays a fixture WAV (3.0s band-limited noise + 2.5s silence @ 44.1kHz mono) through a faithful copy of `WakeWordProducer`'s silence-detection inner loop and calls real `SttGate` against the faster-whisper Docker container. Reports median of 10 measured runs (3 warmup).
- **Files added:** `autoresearch.{md,sh,jsonl}`, `EouLatencyBench.java`, `ARCOS/src/test/resources/audio/eou_fixture.wav`.
- **Pre-optimization known issues** (see `autoresearch.md`): hardcoded 1200ms silence wait, fixed RMS threshold (no adaptive noise floor), sync STT call after full silence window, no HTTP keep-alive across utterances, no STT streaming.
- **Next:** run baseline against shipped defaults, then iterate.
