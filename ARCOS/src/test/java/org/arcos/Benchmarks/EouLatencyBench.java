package org.arcos.Benchmarks;

import org.arcos.Configuration.AudioProperties;
import org.arcos.Configuration.SpeechToTextProperties;
import org.arcos.IO.InputHandling.STT.SttBackendType;
import org.arcos.IO.InputHandling.STT.SttGate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * End-of-Utterance latency benchmark.
 *
 * Measures wall-clock time from "last non-silent audio frame in fixture WAV" until
 * "SttGate.getTranscription() returns". This is the user-perceived latency from
 * "user stops speaking" to "ARCOS could begin reasoning/responding".
 *
 * The benchmark replays a fixed WAV through a harness that mirrors WakeWordProducer's
 * silence-detection inner loop (downsample 44.1kHz -> 16kHz, RMS silence check,
 * silenceDurationMs wait, then SttGate.getTranscription()).
 *
 * EOU = silenceDurationMs wait (post-speech) + STT HTTP round-trip
 *
 * The benchmark is gated behind ARCOS_BENCH=1 to avoid running it during plain `mvn test`.
 * Requires faster-whisper container at http://localhost:8000 (docker compose up -d faster-whisper).
 *
 * Emits a single line on stdout:
 *   METRIC_EOU_MS=&lt;median_ms_over_measured_runs&gt;
 *
 * Tuning knobs (env vars, all optional):
 *   ARCOS_BENCH_WARMUP   default 3
 *   ARCOS_BENCH_MEASURED default 10
 *   ARCOS_BENCH_FIXTURE  default ARCOS/src/test/resources/audio/eou_fixture.wav
 *   ARCOS_BENCH_STT_URL  default http://localhost:8000
 *   ARCOS_BENCH_STT_MODEL default deepdml/faster-whisper-large-v3-turbo-ct2
 *   ARCOS_BENCH_LANG     default fr
 *   (Defaults for silenceThreshold/silenceDurationMs come from application.properties baseline.)
 */
@EnabledIfEnvironmentVariable(named = "ARCOS_BENCH", matches = "1")
class EouLatencyBench {

    // --- constants mirrored from WakeWordProducer ---
    private static final int PORCUPINE_SAMPLE_RATE = 16000;
    private static final int BYTES_PER_SAMPLE = 2;
    private static final int WHISPER_FRAME_SIZE = PORCUPINE_SAMPLE_RATE * BYTES_PER_SAMPLE / 20; // 1600 bytes = 50ms @ 16kHz

    /** 21-tap low-pass FIR (Hamming, fc=7200Hz at 44100Hz) — mirrors WakeWordProducer.LP_FILTER */
    private static final double[] LP_FILTER;
    static {
        int N = 21;
        double fc = 7200.0 / 44100.0;
        LP_FILTER = new double[N];
        double sum = 0;
        int M = N / 2;
        for (int i = 0; i < N; i++) {
            double n = i - M;
            double sinc = (n == 0) ? 2 * Math.PI * fc : Math.sin(2 * Math.PI * fc * n) / (Math.PI * n);
            double hamming = 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (N - 1));
            LP_FILTER[i] = sinc * hamming;
            sum += LP_FILTER[i];
        }
        for (int i = 0; i < N; i++) LP_FILTER[i] /= sum;
    }

    @Test
    void bench_eou_latency() throws Exception {
        int warmup = envInt("ARCOS_BENCH_WARMUP", 3);
        int measured = envInt("ARCOS_BENCH_MEASURED", 10);
        String fixturePath = env("ARCOS_BENCH_FIXTURE", "ARCOS/src/test/resources/audio/eou_fixture.wav");

        // Load production config from application.properties so the bench tracks prod automatically.
        Properties appProps = loadAppProperties();
        AudioProperties audio = audioFromAppProps(appProps);
        SpeechToTextProperties stt = sttFromAppProps(appProps);
        SttBackendType backend = SttBackendType.valueOf(
                appProps.getProperty("arcos.stt.backend", "FASTER_WHISPER").trim());

        // Resolve fixture relative to either the repo root or the ARCOS module dir
        Path fixture = Paths.get(fixturePath);
        if (!Files.exists(fixture)) {
            String alt = fixturePath.startsWith("ARCOS/") ? fixturePath.substring("ARCOS/".length()) : fixturePath;
            fixture = Paths.get(alt);
        }
        if (!Files.exists(fixture)) {
            throw new IllegalStateException("Fixture not found at " + fixturePath + " (cwd=" + Paths.get("").toAbsolutePath() + ")");
        }

        Wav wav = readWav(fixture);
        System.out.printf("BENCH fixture: %s | sampleRate=%d ch=%d duration=%.3fs%n",
                fixture, wav.sampleRate, wav.channels, wav.durationSec());

        String sttUrl = backend == SttBackendType.WHISPER_CPP ? stt.getWhisperCppUrl() : stt.getFasterWhisperUrl();
        System.out.printf("BENCH config: backend=%s silenceThreshold=%d silenceDurationMs=%d sttUrl=%s model=%s lang=%s%n",
                backend, audio.getSilenceThreshold(), audio.getSilenceDurationMs(),
                sttUrl, stt.getFasterWhisperModel(), stt.getLanguage());

        // Warmup
        for (int i = 0; i < warmup; i++) {
            long t = runOne(wav, audio, stt, backend);
            System.out.printf("BENCH warmup[%d]=%dms%n", i, t);
        }

        // Measured
        List<Long> latencies = new ArrayList<>(measured);
        for (int i = 0; i < measured; i++) {
            long t = runOne(wav, audio, stt, backend);
            latencies.add(t);
            System.out.printf("BENCH measured[%d]=%dms%n", i, t);
        }

        Collections.sort(latencies);
        long median = latencies.get(latencies.size() / 2);
        long min = latencies.get(0);
        long max = latencies.get(latencies.size() - 1);
        double mean = latencies.stream().mapToLong(Long::longValue).average().orElse(Double.NaN);

        System.out.printf("BENCH summary: n=%d min=%dms median=%dms mean=%.0fms max=%dms%n",
                measured, min, median, mean, max);
        // Auto-research-parseable line:
        System.out.printf("METRIC_EOU_MS=%d%n", median);
        // Extras (for diagnostics, parsed by autoresearch.sh):
        System.out.printf("METRIC_EOU_MIN_MS=%d%n", min);
        System.out.printf("METRIC_EOU_MAX_MS=%d%n", max);
        System.out.printf("METRIC_EOU_MEAN_MS=%.0f%n", mean);
    }

    /** Single benchmark run: replay WAV in real-time through silence detector + STT. */
    private long runOne(Wav wav, AudioProperties audio, SpeechToTextProperties stt, SttBackendType backend) throws InterruptedException {
        SttGate gate = SttGate.create(backend, stt);
        try {
            gate.reset();

            final int micSampleRate = wav.sampleRate;
            final boolean needsResample = micSampleRate != PORCUPINE_SAMPLE_RATE;
            final int whisperFrameSize = WHISPER_FRAME_SIZE; // 1600 bytes / 50ms
            final int micFrameSize = needsResample
                    ? (int) Math.ceil(whisperFrameSize * micSampleRate / (double) PORCUPINE_SAMPLE_RATE)
                    : whisperFrameSize;

            // Real-time pacing: a mic frame represents (whisperFrameSize/2) samples @ 16kHz = 50ms.
            final long frameDurationNs = 50_000_000L;

            byte[] micBuffer = new byte[micFrameSize];
            byte[] whisperBuffer = new byte[whisperFrameSize];
            final int PRE_BUFFER_FRAMES = 4;
            byte[][] preBuffer = new byte[PRE_BUFFER_FRAMES][];
            int preBufferIndex = 0;

            boolean hasDetectedSpeech = false;
            long lastSoundTime = 0;
            int wavPos = 0;
            int silenceThreshold = audio.getSilenceThreshold();
            long silenceDurationMs = audio.getSilenceDurationMs();

            long startNs = System.nanoTime();
            long t0Ms = System.currentTimeMillis();
            // Ground-truth: the WAV has 3.000s of non-silent audio followed by silence.
            // The "user stops speaking" moment in wall-clock = startMs + 3000.
            // (We use the WAV layout we know we generated; this is intentional.)
            final long endOfSpeechMs = t0Ms + 3000;

            long sttResultMs;
            while (true) {
                long frameDeadline = startNs + ((long) (wavPos / micFrameSize) + 1) * frameDurationNs;

                // Read next mic-sized chunk from WAV
                int avail = Math.min(micFrameSize, wav.pcm.length - wavPos);
                if (avail <= 0) {
                    // Source exhausted before silence triggered — append zero-frames (true silence)
                    java.util.Arrays.fill(micBuffer, (byte) 0);
                } else {
                    System.arraycopy(wav.pcm, wavPos, micBuffer, 0, avail);
                    if (avail < micFrameSize) {
                        java.util.Arrays.fill(micBuffer, avail, micFrameSize, (byte) 0);
                    }
                }
                wavPos += micFrameSize;

                // Resample to 16kHz if needed
                if (needsResample) {
                    int samplesRead = micFrameSize / BYTES_PER_SAMPLE;
                    short[] micSamples = new short[samplesRead];
                    ByteBuffer.wrap(micBuffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(micSamples);
                    int whisperSamples = whisperFrameSize / BYTES_PER_SAMPLE;
                    short[] downsampled = new short[whisperSamples];
                    downsample(micSamples, samplesRead, downsampled, whisperSamples);
                    ByteBuffer bb = ByteBuffer.wrap(whisperBuffer).order(ByteOrder.LITTLE_ENDIAN);
                    bb.clear();
                    for (short s : downsampled) bb.putShort(s);
                } else {
                    System.arraycopy(micBuffer, 0, whisperBuffer, 0, whisperFrameSize);
                }

                if (!hasDetectedSpeech) {
                    preBuffer[preBufferIndex % PRE_BUFFER_FRAMES] = whisperBuffer.clone();
                    preBufferIndex++;
                }

                boolean isSilent = isSilence(whisperBuffer, silenceThreshold);

                if (!isSilent) {
                    lastSoundTime = System.currentTimeMillis();
                    if (!hasDetectedSpeech) {
                        hasDetectedSpeech = true;
                        int oldest = Math.max(0, preBufferIndex - PRE_BUFFER_FRAMES);
                        for (int j = oldest; j < preBufferIndex - 1; j++) {
                            byte[] frame = preBuffer[j % PRE_BUFFER_FRAMES];
                            if (frame != null) gate.processAudio(frame);
                        }
                    }
                }
                if (hasDetectedSpeech) {
                    gate.processAudio(whisperBuffer);
                }

                if (hasDetectedSpeech && isSilent) {
                    long silenceDuration = System.currentTimeMillis() - lastSoundTime;
                    if (silenceDuration >= silenceDurationMs) {
                        // Silence triggered — fire STT and stop the loop
                        gate.getTranscription();
                        sttResultMs = System.currentTimeMillis();
                        break;
                    }
                }

                // Hard safety timeout (15s real-time)
                if (System.currentTimeMillis() - t0Ms > 15_000) {
                    throw new IllegalStateException("Bench loop did not terminate within 15s — silence never triggered. " +
                            "Check silenceThreshold/silenceDurationMs.");
                }

                // Real-time pace: sleep until next 50ms frame boundary
                long sleepNs = frameDeadline - System.nanoTime();
                if (sleepNs > 0) {
                    long sleepMs = sleepNs / 1_000_000L;
                    int sleepRemNs = (int) (sleepNs % 1_000_000L);
                    Thread.sleep(sleepMs, sleepRemNs);
                }
            }

            return sttResultMs - endOfSpeechMs;
        } finally {
            gate.close();
        }
    }

    /** Mirrors WakeWordProducer.isSilence — RMS over signed-16-bit little-endian samples. */
    private static boolean isSilence(byte[] audioData, int silenceThreshold) {
        long sum = 0;
        int sampleCount = audioData.length / 2;
        for (int i = 0; i < audioData.length - 1; i += 2) {
            short sample = (short) ((audioData[i + 1] << 8) | (audioData[i] & 0xFF));
            sum += (long) sample * sample;
        }
        double rms = Math.sqrt((double) sum / sampleCount);
        return rms < silenceThreshold;
    }

    /** Mirrors WakeWordProducer.downsample — FIR-filtered point picker. */
    private static void downsample(short[] input, int inputLength, short[] output, int outputLength) {
        double ratio = (double) inputLength / outputLength;
        int halfTaps = LP_FILTER.length / 2;
        for (int i = 0; i < outputLength; i++) {
            int center = (int) (i * ratio);
            double acc = 0;
            for (int t = 0; t < LP_FILTER.length; t++) {
                int idx = center - halfTaps + t;
                if (idx >= 0 && idx < inputLength) {
                    acc += input[idx] * LP_FILTER[t];
                }
            }
            output[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(acc)));
        }
    }

    /** Load main/resources/application.properties so the bench tracks production config automatically. */
    private static Properties loadAppProperties() throws IOException {
        Properties p = new Properties();
        // Try classpath first (test runtime classpath includes main resources)
        try (InputStream in = EouLatencyBench.class.getResourceAsStream("/application.properties")) {
            if (in != null) {
                p.load(in);
                return p;
            }
        }
        // Fallback: read from disk relative to repo layout
        Path[] candidates = new Path[] {
                Paths.get("ARCOS/src/main/resources/application.properties"),
                Paths.get("src/main/resources/application.properties")
        };
        for (Path c : candidates) {
            if (Files.exists(c)) {
                try (InputStream in = Files.newInputStream(c)) { p.load(in); }
                return p;
            }
        }
        throw new IOException("application.properties not found on classpath or under ARCOS/src/main/resources");
    }

    private static AudioProperties audioFromAppProps(Properties p) {
        AudioProperties a = new AudioProperties();
        a.setSampleRate(Integer.parseInt(p.getProperty("arcos.audio.sample-rate", "44100").trim()));
        a.setSilenceThreshold(Integer.parseInt(p.getProperty("arcos.audio.silence-threshold", "1000").trim()));
        a.setSilenceDurationMs(Integer.parseInt(p.getProperty("arcos.audio.silence-duration-ms", "1200").trim()));
        a.setMaxRecordingSeconds(Integer.parseInt(p.getProperty("arcos.audio.max-recording-seconds", "30").trim()));
        a.setMultiTurnEnabled(Boolean.parseBoolean(p.getProperty("arcos.audio.multi-turn-enabled", "true").trim()));
        a.setPostResponseListeningWindowMs(Integer.parseInt(p.getProperty("arcos.audio.post-response-listening-window-ms", "4000").trim()));
        a.setConversationSilenceMs(Integer.parseInt(p.getProperty("arcos.audio.conversation-silence-ms", "1500").trim()));
        // Env-var overrides (still supported, for sweep experiments without source edits)
        String thr = System.getenv("ARCOS_BENCH_SILENCE_THRESHOLD");
        if (thr != null) a.setSilenceThreshold(Integer.parseInt(thr));
        String dur = System.getenv("ARCOS_BENCH_SILENCE_DURATION_MS");
        if (dur != null) a.setSilenceDurationMs(Integer.parseInt(dur));
        return a;
    }

    private static SpeechToTextProperties sttFromAppProps(Properties p) {
        SpeechToTextProperties s = new SpeechToTextProperties();
        s.setFasterWhisperUrl(p.getProperty("arcos.stt.faster-whisper-url", "http://localhost:8000").trim());
        s.setWhisperCppUrl(p.getProperty("arcos.stt.whisper-cpp-url", "http://localhost:8090").trim());
        s.setFasterWhisperModel(p.getProperty("arcos.stt.faster-whisper-model", "deepdml/faster-whisper-large-v3-turbo-ct2").trim());
        s.setLanguage(p.getProperty("arcos.stt.language", "fr").trim());
        return s;
    }

    // --- WAV helpers (RIFF/WAVE PCM mono/stereo, 16-bit LE) ---
    private static final class Wav {
        final byte[] pcm;
        final int sampleRate;
        final int channels;
        Wav(byte[] pcm, int sampleRate, int channels) {
            this.pcm = pcm; this.sampleRate = sampleRate; this.channels = channels;
        }
        double durationSec() {
            return pcm.length / (double) (sampleRate * channels * 2);
        }
    }

    private static Wav readWav(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            byte[] all = in.readAllBytes();
            // Minimal RIFF parser: locate 'fmt ' and 'data' subchunks
            if (all.length < 44 || all[0] != 'R' || all[1] != 'I' || all[2] != 'F' || all[3] != 'F') {
                throw new IOException("Not a RIFF/WAV file: " + path);
            }
            int channels = 0, sampleRate = 0, bitsPerSample = 0;
            int dataOffset = -1, dataLength = -1;
            int p = 12;
            while (p + 8 <= all.length) {
                String chunkId = new String(all, p, 4);
                int chunkSize = leInt(all, p + 4);
                if ("fmt ".equals(chunkId)) {
                    channels = leShort(all, p + 8 + 2);
                    sampleRate = leInt(all, p + 8 + 4);
                    bitsPerSample = leShort(all, p + 8 + 14);
                } else if ("data".equals(chunkId)) {
                    dataOffset = p + 8;
                    dataLength = chunkSize;
                    break;
                }
                p += 8 + chunkSize;
            }
            if (dataOffset < 0 || bitsPerSample != 16) {
                throw new IOException("Unsupported WAV (need 16-bit PCM with data chunk): " + path);
            }
            byte[] pcm = new byte[dataLength];
            System.arraycopy(all, dataOffset, pcm, 0, dataLength);
            // If stereo, mix down to mono (simple average)
            if (channels == 2) {
                ByteArrayOutputStream mono = new ByteArrayOutputStream(pcm.length / 2);
                for (int i = 0; i + 3 < pcm.length; i += 4) {
                    short l = (short) ((pcm[i + 1] << 8) | (pcm[i] & 0xFF));
                    short r = (short) ((pcm[i + 3] << 8) | (pcm[i + 2] & 0xFF));
                    short m = (short) ((l + r) / 2);
                    mono.write(m & 0xFF);
                    mono.write((m >> 8) & 0xFF);
                }
                return new Wav(mono.toByteArray(), sampleRate, 1);
            }
            return new Wav(pcm, sampleRate, channels);
        }
    }

    private static int leInt(byte[] a, int o) {
        return (a[o] & 0xFF) | ((a[o+1] & 0xFF) << 8) | ((a[o+2] & 0xFF) << 16) | ((a[o+3] & 0xFF) << 24);
    }
    private static int leShort(byte[] a, int o) {
        return (a[o] & 0xFF) | ((a[o+1] & 0xFF) << 8);
    }

    private static int envInt(String name, int dflt) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) return dflt;
        return Integer.parseInt(v.trim());
    }
    private static String env(String name, String dflt) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? dflt : v.trim();
    }
}
