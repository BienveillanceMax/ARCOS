package org.arcos.IO.InputHandling;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;

/**
 * Captures audio via pw-record subprocess.
 * PipeWire handles device routing, volume, and resampling natively.
 * Always outputs 16kHz mono s16le — the native rate for both Porcupine and Whisper.
 */
@Slf4j
public class PipeWireMicrophoneSource implements MicrophoneSource {

    private static final int SAMPLE_RATE = 16000;

    private Process process;
    private InputStream audioStream;

    public PipeWireMicrophoneSource() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "pw-record",
                    "--format", "s16",
                    "--rate", String.valueOf(SAMPLE_RATE),
                    "--channels", "1",
                    "-"   // output to stdout
            );
            pb.redirectErrorStream(false);
            this.process = pb.start();
            this.audioStream = process.getInputStream();

            // Verify the process started (give it a moment to fail if pw-record is missing)
            try { Thread.sleep(100); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            if (!process.isAlive()) {
                int exitCode = process.exitValue();
                log.warn("pw-record exited immediately with code {}", exitCode);
                this.process = null;
                this.audioStream = null;
            } else {
                log.info("PipeWire audio source started ({}Hz, mono, s16le)", SAMPLE_RATE);
            }
        } catch (IOException e) {
            log.debug("pw-record not available: {}", e.getMessage());
            this.process = null;
            this.audioStream = null;
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int length) {
        if (audioStream == null) return -1;
        try {
            return audioStream.read(buffer, offset, length);
        } catch (IOException e) {
            log.error("Error reading from pw-record", e);
            return -1;
        }
    }

    @Override
    public void close() {
        if (process != null) {
            process.destroyForcibly();
            log.info("PipeWire audio source stopped");
        }
    }

    @Override
    public boolean isAvailable() {
        return process != null && process.isAlive();
    }

    @Override
    public String describe() {
        return "pw-record (PipeWire, " + SAMPLE_RATE + "Hz)";
    }

    @Override
    public int getSampleRate() {
        return SAMPLE_RATE;
    }

    @Override
    public int recommendedSilenceThreshold() {
        return 75;
    }

    /**
     * Quick check: is pw-record available on this system?
     */
    public static boolean isPipeWireAvailable() {
        try {
            Process p = new ProcessBuilder("pw-record", "--help")
                    .redirectErrorStream(true)
                    .start();
            p.getInputStream().readAllBytes();
            int exitCode = p.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
