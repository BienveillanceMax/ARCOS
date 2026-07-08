package org.arcos.Benchmarks;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * STT accuracy benchmark: WER matrix over audio_ctx values x real French speech fixtures.
 *
 * Purpose: render a data-backed verdict on the auto-research iters 8-9 (--audio-ctx 500/250),
 * whose latency wins were measured on a noise fixture that structurally cannot exhibit the
 * accuracy regression a reduced encoder context causes on utterances longer than the window
 * (250 mel-frames = 5s, 500 = 10s, 1500 = 30s default).
 *
 * The bench sends audio_ctx as a PER-REQUEST multipart field (honored by whisper.cpp server,
 * verified against the deployed saririus/whisper-cpp-vulkan image on 2026-07-02), so the
 * container's startup flag does not need to change between cells. "dyn" computes
 * ceil(durationSec * 50) + 32 clamped to [128, 1500] — the candidate strategy for
 * WhisperCppAdapter (plan phase P3).
 *
 * Fixtures: src/test/resources/fixtures/speech-fr/utt_*.wav (16kHz mono s16le, Piper
 * fr_FR-siwis-medium — deliberately not the assistant's own GLaDOS voice) with sidecar
 * .txt golden transcripts.
 *
 * Gated behind ARCOS_BENCH=1. Requires the whisper-cpp container (docker compose up -d whisper-cpp).
 *
 * Emits parseable lines:
 *   METRIC_WER_CTX<ctx>_PCT=<mean WER % over fixtures>
 *   METRIC_WER_CTX<ctx>_LONG_PCT=<mean WER % over fixtures &gt; 5s>
 *   METRIC_STT_LAT_CTX<ctx>_MS=<median request latency>
 */
@EnabledIfEnvironmentVariable(named = "ARCOS_BENCH", matches = "1")
class SttAccuracyBench {

    private static final MediaType WAV_TYPE = MediaType.parse("audio/wav");
    private static final String[] CTX_CONFIGS = {"250", "500", "750", "dyn", "1500"};
    private static final double LONG_FIXTURE_THRESHOLD_SEC = 5.0;

    private final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient client = new OkHttpClient.Builder()
            .callTimeout(Duration.ofSeconds(120))
            .build();

    @Test
    void bench_stt_accuracy_matrix() throws Exception {
        String baseUrl = whisperCppUrl();
        Path fixtureDir = resolveFixtureDir();
        List<Path> wavs;
        try (var stream = Files.list(fixtureDir)) {
            wavs = new ArrayList<>(stream.filter(p -> p.getFileName().toString().endsWith(".wav"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList());
        }
        if (wavs.isEmpty()) {
            throw new IllegalStateException("No fixtures in " + fixtureDir);
        }

        System.out.printf("BENCH stt-accuracy: url=%s fixtures=%d ctxConfigs=%s%n",
                baseUrl, wavs.size(), String.join(",", CTX_CONFIGS));
        System.out.println("fixture\tdur_s\tctx\twer_pct\tlat_ms\ttranscript_head");

        // wer[ctxIdx] = per-fixture WERs; latencies likewise
        List<List<Double>> werAll = new ArrayList<>();
        List<List<Double>> werLong = new ArrayList<>();
        List<List<Long>> lats = new ArrayList<>();
        for (int i = 0; i < CTX_CONFIGS.length; i++) {
            werAll.add(new ArrayList<>());
            werLong.add(new ArrayList<>());
            lats.add(new ArrayList<>());
        }

        for (Path wav : wavs) {
            byte[] wavBytes = Files.readAllBytes(wav);
            double durationSec = wavDurationSec(wavBytes);
            String golden = Files.readString(sidecarTxt(wav), StandardCharsets.UTF_8).strip();
            List<String> goldenWords = Wer.normalize(golden);

            for (int c = 0; c < CTX_CONFIGS.length; c++) {
                String cfg = CTX_CONFIGS[c];
                int audioCtx = "dyn".equals(cfg) ? dynamicAudioCtx(durationSec) : Integer.parseInt(cfg);

                long t0 = System.currentTimeMillis();
                String transcript = transcribe(baseUrl, wavBytes, audioCtx);
                long latMs = System.currentTimeMillis() - t0;

                double wer = Wer.wer(goldenWords, Wer.normalize(transcript)) * 100.0;
                werAll.get(c).add(wer);
                if (durationSec > LONG_FIXTURE_THRESHOLD_SEC) {
                    werLong.get(c).add(wer);
                }
                lats.get(c).add(latMs);

                String head = transcript.strip();
                if (head.length() > 60) head = head.substring(0, 60) + "…";
                System.out.printf(Locale.ROOT, "%s\t%.1f\t%s(%d)\t%.1f\t%d\t%s%n",
                        wav.getFileName(), durationSec, cfg, audioCtx, wer, latMs, head);
            }
        }

        System.out.println("--- summary (mean WER %, median latency ms) ---");
        for (int c = 0; c < CTX_CONFIGS.length; c++) {
            String cfg = CTX_CONFIGS[c].toUpperCase(Locale.ROOT);
            double meanAll = mean(werAll.get(c));
            double meanLong = mean(werLong.get(c));
            long medLat = median(lats.get(c));
            System.out.printf(Locale.ROOT, "METRIC_WER_CTX%s_PCT=%.1f%n", cfg, meanAll);
            System.out.printf(Locale.ROOT, "METRIC_WER_CTX%s_LONG_PCT=%.1f%n", cfg, meanLong);
            System.out.printf(Locale.ROOT, "METRIC_STT_LAT_CTX%s_MS=%d%n", cfg, medLat);
        }
    }

    /** Candidate dynamic strategy (plan P3): ~50 mel-frames per second of audio + safety margin. */
    private static int dynamicAudioCtx(double durationSec) {
        int ctx = (int) Math.ceil(durationSec * 50) + 32;
        return Math.max(128, Math.min(1500, ctx));
    }

    private String transcribe(String baseUrl, byte[] wavBytes, int audioCtx) throws IOException {
        RequestBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "audio.wav", RequestBody.create(wavBytes, WAV_TYPE))
                .addFormDataPart("temperature", "0")
                .addFormDataPart("response_format", "json")
                .addFormDataPart("audio_ctx", String.valueOf(audioCtx))
                .build();
        Request request = new Request.Builder().url(baseUrl + "/inference").post(body).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("whisper-cpp HTTP " + response.code());
            }
            return mapper.readTree(response.body().string()).path("text").asText("");
        }
    }

    // --- plumbing ---

    private static String whisperCppUrl() throws IOException {
        Properties p = new Properties();
        try (InputStream in = SttAccuracyBench.class.getResourceAsStream("/application.properties")) {
            if (in != null) p.load(in);
        }
        return p.getProperty("arcos.stt.whisper-cpp-url", "http://localhost:8090").trim();
    }

    private static Path resolveFixtureDir() {
        Path[] candidates = {
                Paths.get("src/test/resources/fixtures/speech-fr"),
                Paths.get("ARCOS/src/test/resources/fixtures/speech-fr")
        };
        for (Path c : candidates) {
            if (Files.isDirectory(c)) return c;
        }
        throw new IllegalStateException("Fixture dir not found (cwd=" + Paths.get("").toAbsolutePath() + ")");
    }

    private static Path sidecarTxt(Path wav) {
        String name = wav.getFileName().toString().replaceFirst("\\.wav$", ".txt");
        return wav.resolveSibling(name);
    }

    /** Duration of a 16kHz mono s16le WAV (44-byte canonical header assumed for our generated fixtures). */
    private static double wavDurationSec(byte[] wav) {
        int sampleRate = (wav[24] & 0xFF) | ((wav[25] & 0xFF) << 8) | ((wav[26] & 0xFF) << 16) | ((wav[27] & 0xFF) << 24);
        return (wav.length - 44) / (double) (sampleRate * 2);
    }

    private static double mean(List<Double> xs) {
        return xs.isEmpty() ? Double.NaN : xs.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
    }

    private static long median(List<Long> xs) {
        if (xs.isEmpty()) return -1;
        List<Long> sorted = xs.stream().sorted().toList();
        return sorted.get(sorted.size() / 2);
    }
}
