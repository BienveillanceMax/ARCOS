package org.arcos.Benchmarks;

import org.arcos.Configuration.AudioProperties;
import org.arcos.Configuration.SpeechToTextProperties;
import org.arcos.IO.InputHandling.AudioFraming;
import org.arcos.IO.InputHandling.CaptureConfig;
import org.arcos.IO.InputHandling.MicrophoneSource;
import org.arcos.IO.InputHandling.STT.SttBackendType;
import org.arcos.IO.InputHandling.STT.SttGate;
import org.arcos.IO.InputHandling.UtteranceCaptureService;
import org.arcos.IO.Telemetry.TurnTimeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.locks.LockSupport;

/**
 * End-of-Utterance latency benchmark.
 *
 * Mesure le temps entre "dernier octet de parole servi par le micro" et "transcription
 * disponible" — la latence perçue entre la fin de parole utilisateur et le moment où
 * ARCOS peut commencer à raisonner.
 *
 * Depuis la refonte P1, le bench exerce le VRAI code de capture de production
 * ({@link UtteranceCaptureService} : VAD, fin d'énoncé, STT spéculatif avec débounce et
 * annulation) au lieu d'un clone — via une {@link MicrophoneSource} qui rejoue en temps
 * réel une fixture de vraie parole française (voir fixtures/speech-fr, générées en P0),
 * suivie de silence synthétique. Le seuil VAD se résout comme en production
 * ({@link UtteranceCaptureService#resolveSilenceThreshold}). Deux scénarios sont mesurés :
 * le chemin wake-word et la fenêtre de conversation (silence de fin différent).
 *
 * Les chiffres ne sont PAS comparables aux runs de la campagne auto-research iters 0-9
 * (ancien clone : fixture de bruit, seuil VAD 1000, fin de parole codée en dur).
 *
 * Gated derrière ARCOS_BENCH=1. Nécessite le conteneur STT du backend configuré.
 *
 * Lignes parsables :
 *   METRIC_EOU_MS=&lt;médiane wake&gt;   (+ MIN/MAX/MEAN)
 *   METRIC_EOU_CONV_MS=&lt;médiane conversation&gt;
 *   METRIC_EOU_WER_PCT=&lt;WER moyen des transcriptions mesurées, garde-fou hallucination&gt;
 *
 * Env optionnels : ARCOS_BENCH_WARMUP (3), ARCOS_BENCH_MEASURED (10),
 * ARCOS_BENCH_FIXTURE (fixtures/speech-fr/utt_05s_dentiste.wav),
 * ARCOS_BENCH_SILENCE_THRESHOLD, ARCOS_BENCH_SILENCE_DURATION_MS.
 */
@EnabledIfEnvironmentVariable(named = "ARCOS_BENCH", matches = "1")
class EouLatencyBench {

    private static final int SAMPLE_RATE = 16000;
    private static final String DEFAULT_FIXTURE = "src/test/resources/fixtures/speech-fr/utt_05s_dentiste.wav";

    @Test
    void bench_eou_latency() throws Exception {
        int warmup = envInt("ARCOS_BENCH_WARMUP", 3);
        int measured = envInt("ARCOS_BENCH_MEASURED", 10);

        Properties appProps = loadAppProperties();
        AudioProperties audio = audioFromAppProps(appProps);
        SpeechToTextProperties stt = sttFromAppProps(appProps);
        SttBackendType backend = SttBackendType.valueOf(
                appProps.getProperty("arcos.stt.backend", "FASTER_WHISPER").trim());

        Path fixture = resolveFixture(env("ARCOS_BENCH_FIXTURE", DEFAULT_FIXTURE));
        Wav wav = readWav(fixture);
        if (wav.sampleRate != SAMPLE_RATE) {
            throw new IllegalStateException("Fixture must be 16kHz mono (got " + wav.sampleRate + "Hz): " + fixture);
        }
        String golden = readGolden(fixture);

        int silenceThreshold = UtteranceCaptureService.resolveSilenceThreshold(
                audio, new FixtureMicrophoneSource(wav.pcm));
        String sttUrl = backend == SttBackendType.WHISPER_CPP ? stt.getWhisperCppUrl() : stt.getFasterWhisperUrl();
        System.out.printf("BENCH fixture: %s | duration=%.3fs | golden=\"%s\"%n", fixture, wav.durationSec(), golden);
        System.out.printf("BENCH config: backend=%s silenceThreshold=%d silenceDurationMs=%d conversationSilenceMs=%d sttUrl=%s lang=%s%n",
                backend, silenceThreshold, audio.getSilenceDurationMs(), audio.getConversationSilenceMs(),
                sttUrl, stt.getLanguage());

        // Warmup (chemin wake)
        for (int i = 0; i < warmup; i++) {
            Run r = runOne(wav, golden, CaptureConfig.forWake(audio), audio, stt, backend);
            System.out.printf("BENCH warmup[%d]=%dms%n", i, r.eouMs);
        }

        // Mesures : wake puis conversation
        List<Run> wakeRuns = new ArrayList<>(measured);
        for (int i = 0; i < measured; i++) {
            Run r = runOne(wav, golden, CaptureConfig.forWake(audio), audio, stt, backend);
            wakeRuns.add(r);
            System.out.printf("BENCH wake[%d]=%dms wer=%.1f%%%n", i, r.eouMs, r.werPct);
        }
        List<Run> convRuns = new ArrayList<>(measured);
        for (int i = 0; i < measured; i++) {
            Run r = runOne(wav, golden, CaptureConfig.forConversation(audio, 4000), audio, stt, backend);
            convRuns.add(r);
            System.out.printf("BENCH conv[%d]=%dms wer=%.1f%%%n", i, r.eouMs, r.werPct);
        }

        summarize("wake", wakeRuns, "METRIC_EOU");
        summarize("conversation", convRuns, "METRIC_EOU_CONV");

        double meanWer = wakeRuns.stream().mapToDouble(r -> r.werPct).average().orElse(Double.NaN);
        System.out.printf(Locale.ROOT, "METRIC_EOU_WER_PCT=%.1f%n", meanWer);
        if (meanWer > 30.0) {
            System.out.println("BENCH WARNING: WER > 30% — la latence mesurée inclut probablement un chemin d'hallucination, chiffres suspects.");
        }
    }

    private record Run(long eouMs, double werPct) { }

    /** Un run : rejoue la fixture en temps réel à travers le service de capture de production. */
    private Run runOne(Wav wav, String golden, CaptureConfig config, AudioProperties audio,
                       SpeechToTextProperties stt, SttBackendType backend) {
        FixtureMicrophoneSource source = new FixtureMicrophoneSource(wav.pcm);
        int threshold = UtteranceCaptureService.resolveSilenceThreshold(audio, source);
        SttGate gate = SttGate.create(backend, stt);
        try (UtteranceCaptureService service = new UtteranceCaptureService(source, gate, threshold, new TurnTimeline())) {
            var result = service.capture(config);
            long eou = System.currentTimeMillis() - source.lastSpeechServedAtMs();
            double werPct = golden == null ? Double.NaN : Wer.compute(golden, result.text()) * 100.0;
            return new Run(eou, werPct);
        } finally {
            gate.close();
        }
    }

    private static String readGolden(Path fixture) throws IOException {
        Path txt = fixture.resolveSibling(fixture.getFileName().toString().replaceFirst("\\.wav$", ".txt"));
        return Files.exists(txt) ? Files.readString(txt, StandardCharsets.UTF_8).strip() : null;
    }

    private static void summarize(String label, List<Run> runs, String metricPrefix) {
        List<Long> lats = new ArrayList<>(runs.stream().map(Run::eouMs).toList());
        Collections.sort(lats);
        long median = lats.get(lats.size() / 2);
        long min = lats.get(0);
        long max = lats.get(lats.size() - 1);
        double mean = lats.stream().mapToLong(Long::longValue).average().orElse(Double.NaN);
        System.out.printf("BENCH %s summary: n=%d min=%dms median=%dms mean=%.0fms max=%dms%n",
                label, runs.size(), min, median, mean, max);
        System.out.printf("%s_MS=%d%n", metricPrefix, median);
        System.out.printf("%s_MIN_MS=%d%n", metricPrefix, min);
        System.out.printf("%s_MAX_MS=%d%n", metricPrefix, max);
        System.out.printf(Locale.ROOT, "%s_MEAN_MS=%.0f%n", metricPrefix, mean);
    }

    /**
     * Source micro de fixture : sert la parole 16kHz en temps réel (une trame de 50ms par
     * read()), puis du silence pur une fois la fixture épuisée. Mémorise l'instant où le
     * dernier octet de parole a été servi — la référence "l'utilisateur a fini de parler"
     * du calcul d'EOU (remplace le t0+3000ms codé en dur de l'ancien bench).
     */
    static final class FixtureMicrophoneSource implements MicrophoneSource {
        private final byte[] pcm;
        private int pos = 0;
        private long startNs = -1;
        private int frameIndex = 0;
        private volatile long lastSpeechServedAtMs = -1;

        FixtureMicrophoneSource(byte[] pcm) {
            this.pcm = pcm;
        }

        long lastSpeechServedAtMs() {
            return lastSpeechServedAtMs;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            if (startNs < 0) startNs = System.nanoTime();
            long deadline = startNs + (frameIndex + 1) * 50_000_000L;
            long sleepNs = deadline - System.nanoTime();
            if (sleepNs > 0) LockSupport.parkNanos(sleepNs);
            frameIndex++;

            int avail = Math.min(length, pcm.length - pos);
            if (avail > 0) {
                System.arraycopy(pcm, pos, buffer, offset, avail);
                pos += avail;
                if (avail < length) {
                    Arrays.fill(buffer, offset + avail, offset + length, (byte) 0);
                }
                // Référence EOU = dernière trame NON silencieuse servie (la fin de la fixture
                // peut être quasi silencieuse — queue de synthèse Piper — et la capture peut
                // couper dessus avant d'avoir servi le dernier octet).
                byte[] frame = offset == 0 && length == buffer.length
                        ? buffer : Arrays.copyOfRange(buffer, offset, offset + length);
                if (!AudioFraming.isSilence(frame, recommendedSilenceThreshold())) {
                    lastSpeechServedAtMs = System.currentTimeMillis();
                }
            } else {
                Arrays.fill(buffer, offset, offset + length, (byte) 0);
            }
            return length;
        }

        @Override public void close() { }
        @Override public boolean isAvailable() { return true; }
        @Override public String describe() { return "fixture-replay (16kHz, " + pcm.length + " bytes)"; }
        @Override public int getSampleRate() { return SAMPLE_RATE; }
        @Override public int recommendedSilenceThreshold() { return 75; } // aligné PipeWire (prod)
    }

    // --- plumbing (inchangé : lecture de la config de prod + parsing WAV) ---

    private static Path resolveFixture(String fixturePath) {
        Path fixture = Paths.get(fixturePath);
        if (!Files.exists(fixture) && fixturePath.startsWith("ARCOS/")) {
            fixture = Paths.get(fixturePath.substring("ARCOS/".length()));
        }
        if (!Files.exists(fixture)) {
            Path alt = Paths.get("ARCOS").resolve(fixturePath);
            if (Files.exists(alt)) fixture = alt;
        }
        if (!Files.exists(fixture)) {
            throw new IllegalStateException("Fixture not found at " + fixturePath + " (cwd=" + Paths.get("").toAbsolutePath() + ")");
        }
        return fixture;
    }

    /** Load main/resources/application.properties so the bench tracks production config automatically. */
    private static Properties loadAppProperties() throws IOException {
        Properties p = new Properties();
        try (InputStream in = EouLatencyBench.class.getResourceAsStream("/application.properties")) {
            if (in != null) {
                p.load(in);
                return p;
            }
        }
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
        a.setSilenceThreshold(Integer.parseInt(p.getProperty("arcos.audio.silence-threshold", "-1").trim()));
        a.setSilenceDurationMs(Integer.parseInt(p.getProperty("arcos.audio.silence-duration-ms", "1200").trim()));
        a.setMaxRecordingSeconds(Integer.parseInt(p.getProperty("arcos.audio.max-recording-seconds", "30").trim()));
        a.setMultiTurnEnabled(Boolean.parseBoolean(p.getProperty("arcos.audio.multi-turn-enabled", "true").trim()));
        a.setPostResponseListeningWindowMs(Integer.parseInt(p.getProperty("arcos.audio.post-response-listening-window-ms", "4000").trim()));
        a.setConversationSilenceMs(Integer.parseInt(p.getProperty("arcos.audio.conversation-silence-ms", "1500").trim()));
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
