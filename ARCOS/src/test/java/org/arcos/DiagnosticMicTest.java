package org.arcos;

import ai.picovoice.porcupine.Porcupine;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Standalone diagnostic: tests the full pipeline pw-record → Porcupine.
 * Run with: mvn exec:java -Dexec.mainClass=org.arcos.DiagnosticMicTest -Dexec.classpathScope=test
 */
public class DiagnosticMicTest {

    public static void main(String[] args) throws Exception {
        String accessKey = System.getenv("PORCUPINE_ACCESS_KEY");
        if (accessKey == null || accessKey.isBlank()) {
            System.out.println("ERROR: PORCUPINE_ACCESS_KEY not set. Run: source .env && export PORCUPINE_ACCESS_KEY");
            return;
        }
        System.out.println("[OK] PORCUPINE_ACCESS_KEY set (length=" + accessKey.length() + ")");

        // Extract keyword and model
        String keywordPath = extractResource("Mon-ami_fr_linux_v3_0_0.ppn");
        String modelPath = extractResource("porcupine_params_fr.pv");
        System.out.println("[OK] Keyword: " + keywordPath);
        System.out.println("[OK] Model: " + modelPath);

        // Init Porcupine
        Porcupine porcupine;
        try {
            porcupine = new Porcupine.Builder()
                    .setAccessKey(accessKey)
                    .setKeywordPaths(new String[]{keywordPath})
                    .setModelPath(modelPath)
                    .setSensitivities(new float[]{0.9f})
                    .build();
            System.out.println("[OK] Porcupine initialized (frameLength=" + porcupine.getFrameLength() + ", sampleRate=" + porcupine.getSampleRate() + ")");
        } catch (Exception e) {
            System.out.println("ERROR: Porcupine init failed: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // Start pw-record at Porcupine's native rate
        int rate = porcupine.getSampleRate();
        System.out.println("[..] Starting pw-record at " + rate + "Hz...");
        ProcessBuilder pb = new ProcessBuilder("pw-record", "--format", "s16", "--rate", String.valueOf(rate), "--channels", "1", "-");
        pb.redirectErrorStream(false);
        Process proc = pb.start();
        Thread.sleep(200);
        if (!proc.isAlive()) {
            System.out.println("ERROR: pw-record failed to start");
            porcupine.delete();
            return;
        }
        System.out.println("[OK] pw-record started");

        InputStream audio = proc.getInputStream();
        int frameLength = porcupine.getFrameLength();
        int frameBytes = frameLength * 2;
        byte[] buf = new byte[frameBytes];
        short[] samples = new short[frameLength];

        System.out.println();
        System.out.println("=== LISTENING FOR 30 SECONDS — say 'Mon ami' ===");
        System.out.println("(RMS printed every 2 seconds)");
        System.out.println();

        long startTime = System.currentTimeMillis();
        long lastRmsTime = 0;
        int framesProcessed = 0;

        try {
            while (System.currentTimeMillis() - startTime < 30000) {
                // Read exactly one frame
                int total = 0;
                while (total < frameBytes) {
                    int r = audio.read(buf, total, frameBytes - total);
                    if (r < 0) {
                        System.out.println("ERROR: pw-record stream ended");
                        return;
                    }
                    total += r;
                }

                // Convert to shorts
                ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples);
                framesProcessed++;

                // RMS logging
                long now = System.currentTimeMillis();
                if (now - lastRmsTime > 2000) {
                    long sum = 0;
                    short peak = 0;
                    for (short s : samples) {
                        sum += (long) s * s;
                        if (Math.abs(s) > peak) peak = (short) Math.abs(s);
                    }
                    int rms = (int) Math.sqrt((double) sum / frameLength);
                    System.out.printf("  [%ds] RMS=%d peak=%d frames=%d%n",
                            (now - startTime) / 1000, rms, peak, framesProcessed);
                    lastRmsTime = now;
                }

                // Porcupine process
                int result = porcupine.process(samples);
                if (result >= 0) {
                    System.out.println(">>> WAKE WORD DETECTED! (keyword index " + result + ") <<<");
                }
            }
        } finally {
            proc.destroyForcibly();
            porcupine.delete();
            System.out.println();
            System.out.println("Done. Processed " + framesProcessed + " frames in 30s.");
        }
    }

    private static String extractResource(String name) throws Exception {
        try (InputStream in = DiagnosticMicTest.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) throw new IllegalArgumentException("Resource not found: " + name);
            Path tmp = Files.createTempFile(name, "");
            tmp.toFile().deleteOnExit();
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            return tmp.toAbsolutePath().toString();
        }
    }
}
