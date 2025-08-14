package IO.InputHandling;

import javax.sound.sampled.TargetDataLine;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CompletableFuture;

public class AudioForwarder extends Thread {
    private TargetDataLine microphone;
    private SpeechToText speechToText;
    private boolean isForwarding;
    private CompletableFuture<String> messageCompleteFuture;

    // Silence detection parameters
    private static final int SILENCE_THRESHOLD = 1000; // Adjust based on your environment
    private static final long SILENCE_DURATION_MS = 2000; // 3 seconds
    private static final int SAMPLE_RATE = 16000;
    private static final int BYTES_PER_SAMPLE = 2; // 16-bit audio
    private static final int FRAME_SIZE = SAMPLE_RATE * BYTES_PER_SAMPLE / 20; // 50ms frames for better responsiveness

    private long lastSoundTime;
    private boolean hasDetectedSpeech;
    private long recordingStartTime;

    public AudioForwarder(TargetDataLine microphone, SpeechToText speechToText) {
        this.microphone = microphone;
        this.speechToText = speechToText;
        this.isForwarding = false;
        this.hasDetectedSpeech = false;
        this.setDaemon(true); // Make this a daemon thread
    }

    public CompletableFuture<String> startForwarding() {
        if (this.isForwarding) {
            return CompletableFuture.completedFuture(""); // Already running
        }

        this.isForwarding = true;
        this.hasDetectedSpeech = false;
        this.lastSoundTime = System.currentTimeMillis();
        this.recordingStartTime = System.currentTimeMillis();
        this.messageCompleteFuture = new CompletableFuture<>();

        // Reset speech to text for new session
        this.speechToText.reset();

        // Start the thread
        Thread newThread = new Thread(this);
        newThread.setDaemon(true);
        newThread.start();

        return this.messageCompleteFuture;
    }

    public void stopForwarding() {
        this.isForwarding = false;
        this.interrupt();
        if (this.messageCompleteFuture != null && !this.messageCompleteFuture.isDone()) {
            this.messageCompleteFuture.complete("");
        }
    }

    private boolean isSilence(byte[] audioData) {
        // Convert bytes to 16-bit samples and calculate RMS
        long sum = 0;
        int sampleCount = audioData.length / 2;

        for (int i = 0; i < audioData.length - 1; i += 2) {
            // Little-endian 16-bit sample
            short sample = (short) ((audioData[i + 1] << 8) | (audioData[i] & 0xFF));
            sum += sample * sample;
        }

        double rms = Math.sqrt((double) sum / sampleCount);
        System.out.println("RMS: " + rms);
        return rms < SILENCE_THRESHOLD;
    }

    @Override
    public void run() {
        ByteBuffer audioBuffer = ByteBuffer.allocate(FRAME_SIZE);
        audioBuffer.order(ByteOrder.LITTLE_ENDIAN);

        System.out.println("Started listening for speech with WhisperJNI...");

        try {
            while (this.isForwarding) {
                int numBytesRead = this.microphone.read(audioBuffer.array(), 0, FRAME_SIZE);

                if (numBytesRead > 0) {
                    byte[] audioData = new byte[numBytesRead];
                    System.arraycopy(audioBuffer.array(), 0, audioData, 0, numBytesRead);

                    // Check for silence
                    boolean isSilent = isSilence(audioData);
                    System.out.println("SpeechToText isSilent: " + isSilent);

                    if (!isSilent) {
                        this.lastSoundTime = System.currentTimeMillis();
                        if (!this.hasDetectedSpeech) {
                            this.hasDetectedSpeech = true;
                            System.out.println("Speech detected, starting transcription...");
                        }
                    }

                    // Always buffer audio data for Whisper
                    this.speechToText.processAudio(audioData);

                    // Check if we should stop due to silence
                    if (this.hasDetectedSpeech && isSilent) {
                        long silenceDuration = System.currentTimeMillis() - this.lastSoundTime;
                        if (silenceDuration >= SILENCE_DURATION_MS) {
                            System.out.println("Detected " + SILENCE_DURATION_MS + "ms of silence, processing transcription...");
                            break;
                        }
                    }

                    // Safety timeout : don't record for more than 30 seconds
                    long totalRecordingTime = System.currentTimeMillis() - this.recordingStartTime;
                    if (totalRecordingTime > 30000) {
                        System.out.println("Maximum recording time reached (30s), processing transcription...");
                        break;
                    }

                } else {
                    // No bytes read, small delay to prevent busy waiting
                    Thread.sleep(10);
                }
            }
        } catch (InterruptedException e) {
            System.out.println("Audio forwarding interrupted");
        } catch (Exception e) {
            System.err.println("Error in audio forwarding: " + e.getMessage());
            e.printStackTrace();
        } finally {
            processTranscription();
        }
    }

    private void processTranscription() {
        try {
            String transcription = "";

            // Check if we have enough audio for transcription
            if (this.speechToText.hasMinimumAudio()) {
                System.out.println("Processing " + this.speechToText.getBufferedAudioDurationMs() + "ms of audio...");
                transcription = this.speechToText.getTranscription();
            } else {
                System.out.println("Not enough audio data for transcription (" +
                        this.speechToText.getBufferedAudioDurationMs() + "ms < " +
                        this.speechToText.getMinimumAudioDurationMs() + "ms)");
            }

            if (!transcription.isEmpty()) {
                System.out.println("Whisper transcription: \"" + transcription + "\"");
            } else {
                System.out.println("No speech detected or transcription failed");
            }

            this.isForwarding = false;
            if (this.messageCompleteFuture != null) {
                this.messageCompleteFuture.complete(transcription);
            }

        } catch (Exception e) {
            System.err.println("Error during transcription processing: " + e.getMessage());
            e.printStackTrace();

            this.isForwarding = false;
            if (this.messageCompleteFuture != null) {
                this.messageCompleteFuture.complete("");
            }
        }
    }
}