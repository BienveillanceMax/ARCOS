# CHANGELOG — ARCOS

Lab notebook for substantial work. Newest entries first.

## 2026-06-22 — Autoresearch session complete: audio-IO EOU latency 6800ms → 232ms (−96.6%)

9 iterations on branch `optim/audio-io-latency`. Stop-target (≤400ms) hit at iter 9. **Crossed acceptance (≤2000ms) at iter 3 and stretch (≤1000ms) at iter 8.** Full per-iter detail in `autoresearch.md`; raw runs in `autoresearch.jsonl`.

**Production changes shipped on this branch:**
- `application.properties`: `silence-duration-ms` 1200→200, `conversation-silence-ms` 1500→500, `stt.backend` FASTER_WHISPER→WHISPER_CPP.
- `ARCOS/docker-compose.yml`: whisper-cpp `command:` override adds `--audio-ctx 250` (caps encoder mel context to 5s, ~6× encoder speedup).
- New `org.arcos.IO.InputHandling.AudioFraming` (static `isSilence`, `downsample`, `LP_FILTER`) extracted from `WakeWordProducer`.
- `Producers/WakeWordProducer`: speculative STT launched at silence-onset (overlaps silence-wait with STT HTTP round-trip); silent frames no longer buffered into `SttGate`.

**Final EOU breakdown** (median 232 ms): ~50–80 ms pre-STT (silence-wait residual after speculation overlap) + ~150–180 ms STT compute (whisper.cpp Vulkan, large-v3-turbo, audio-ctx=250).

**Known limitations:**
- `--audio-ctx 250` truncates utterances longer than 5 s. Most ARCOS voice commands fit; long dictation-style input would lose tail content. **Validate against real-speech fixture before deploying widely.**
- The bench fixture is band-limited noise, which is encoder-bound; latency claims are robust, but transcript-quality regressions from `audio-ctx 250` are not directly measured. Decode-tuning flags (`--best-of 1`, `--no-fallback`, `--suppress-nst`) were tried in iter 6 and reverted because the noise fixture under-represents decoder work; they may still help on real speech and are worth re-testing.
- The whisper.cpp container reports its Vulkan device as `Intel(R) Graphics (LNL)`, not the AMD Radeon 780M that prior project notes recorded. Latency wins are real regardless; the hardware identity discrepancy is flagged for the operator to verify.

## 2026-06-22 — Start autoresearch loop: audio-IO end-of-utterance latency

- **Branch:** `optim/audio-io-latency` (off `main`, 14 commits ahead of origin).
- **Goal:** minimize wall-clock `EOU_LATENCY_MS` = (silence-detection wait + STT round-trip).
- **Harness:** new test `ARCOS/src/test/java/org/arcos/Benchmarks/EouLatencyBench.java`, gated by `ARCOS_BENCH=1`. Replays a fixture WAV (3.0s band-limited noise + 2.5s silence @ 44.1kHz mono) through a faithful copy of `WakeWordProducer`'s silence-detection inner loop and calls real `SttGate` against the faster-whisper Docker container. Reports median of 10 measured runs (3 warmup).
- **Files added:** `autoresearch.{md,sh,jsonl}`, `EouLatencyBench.java`, `ARCOS/src/test/resources/audio/eou_fixture.wav`.
- **Pre-optimization known issues** (see `autoresearch.md`): hardcoded 1200ms silence wait, fixed RMS threshold (no adaptive noise floor), sync STT call after full silence window, no HTTP keep-alive across utterances, no STT streaming.
- **Next:** run baseline against shipped defaults, then iterate.
