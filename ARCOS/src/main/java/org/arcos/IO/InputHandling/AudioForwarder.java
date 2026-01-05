package org.arcos.IO.InputHandling;

import lombok.extern.slf4j.Slf4j;

import javax.sound.sampled.TargetDataLine;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class AudioForwarder {
    private TargetDataLine microphone;
    private SpeechToText speechToText;
    private final AtomicBoolean isForwarding = new AtomicBoolean(false);
    private Thread currentThread;

    // Silence detection parameters
    private static final int SILENCE_THRESHOLD = 1000;
    private static final long SILENCE_DURATION_MS = 2000;
    private static final int SAMPLE_RATE = 16000;
    private static final int BYTES_PER_SAMPLE = 2;
    private static final int FRAME_SIZE = SAMPLE_RATE * BYTES_PER_SAMPLE / 20;

    public AudioForwarder(TargetDataLine microphone, SpeechToText speechToText) {
        this.microphone = microphone;
        this.speechToText = speechToText;
    }

    public CompletableFuture<String> startForwarding() {
        // Check if already running and stop previous thread if needed
        if (isForwarding.get()) {
            log.warn("AudioForwarder already running, stopping previous session");
            stopForwarding();
            // Give it a moment to clean up
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        isForwarding.set(true);
        CompletableFuture<String> messageCompleteFuture = new CompletableFuture<>();

        // Reset speech to text for new session
        this.speechToText.reset();

        // Create and start the recording thread
        currentThread = new Thread(() -> runRecording(messageCompleteFuture));
        currentThread.setDaemon(true);
        currentThread.start();

        return messageCompleteFuture;
    }

    public void stopForwarding() {
        isForwarding.set(false);
        if (currentThread != null && currentThread.isAlive()) {
            currentThread.interrupt();
        }
    }

    private boolean isSilence(byte[] audioData) {
        long sum = 0;
        int sampleCount = audioData.length / 2;

        for (int i = 0; i < audioData.length - 1; i += 2) {
            short sample = (short) ((audioData[i + 1] << 8) | (audioData[i] & 0xFF));
            sum += sample * sample;
        }

        double rms = Math.sqrt((double) sum / sampleCount);
        log.debug("RMS: {}", rms);
        return rms < SILENCE_THRESHOLD;
    }

    private void runRecording(CompletableFuture<String> messageCompleteFuture) {
        ByteBuffer audioBuffer = ByteBuffer.allocate(FRAME_SIZE);
        audioBuffer.order(ByteOrder.LITTLE_ENDIAN);

        long lastSoundTime = System.currentTimeMillis();
        long recordingStartTime = System.currentTimeMillis();
        boolean hasDetectedSpeech = false;

        log.info("Started listening for speech with WhisperJNI...");

        try {
            while (isForwarding.get()) {
                int numBytesRead = this.microphone.read(audioBuffer.array(), 0, FRAME_SIZE);

                if (numBytesRead > 0) {
                    byte[] audioData = new byte[numBytesRead];
                    System.arraycopy(audioBuffer.array(), 0, audioData, 0, numBytesRead);

                    boolean isSilent = isSilence(audioData);
                    log.debug("SpeechToText isSilent: {}", isSilent);

                    if (!isSilent) {
                        lastSoundTime = System.currentTimeMillis();
                        if (!hasDetectedSpeech) {
                            hasDetectedSpeech = true;
                            log.info("Speech detected, starting transcription...");
                        }
                    }

                    // Always buffer audio data for Whisper
                    this.speechToText.processAudio(audioData);

                    // Check if we should stop due to silence
                    if (hasDetectedSpeech && isSilent) {
                        long silenceDuration = System.currentTimeMillis() - lastSoundTime;
                        if (silenceDuration >= SILENCE_DURATION_MS) {
                            log.info("Detected {}ms of silence, processing transcription...", SILENCE_DURATION_MS);
                            break;
                        }
                    }

                    // Safety timeout
                    long totalRecordingTime = System.currentTimeMillis() - recordingStartTime;
                    if (totalRecordingTime > 30000) {
                        log.info("Maximum recording time reached (30s), processing transcription...");
                        break;
                    }

                } else {
                    Thread.sleep(10);
                }
            }
        } catch (InterruptedException e) {
            log.info("Audio forwarding interrupted");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Error in audio forwarding", e);
        } finally {
            processTranscription(messageCompleteFuture);
            isForwarding.set(false);
        }
    }

    private void processTranscription(CompletableFuture<String> messageCompleteFuture) {
        try {
            String transcription = "";

            if (this.speechToText.hasMinimumAudio()) {
                log.info("Processing {}ms of audio...", this.speechToText.getBufferedAudioDurationMs());
                transcription = this.speechToText.getTranscription();
            } else {
                log.info("Not enough audio data for transcription ({}ms < {}ms)",
                        this.speechToText.getBufferedAudioDurationMs(),
                        this.speechToText.getMinimumAudioDurationMs());
            }

            if (!transcription.isEmpty()) {
                log.info("Whisper transcription: \"{}\"", transcription);
            } else {
                log.info("No speech detected or transcription failed");
            }

            messageCompleteFuture.complete(transcription);

        } catch (Exception e) {
            log.error("Error during transcription processing", e);
            messageCompleteFuture.complete("");
        }
    }
}