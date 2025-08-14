package EventLoop.InputHandling;

import com.arcos.bus.EventBus;
import com.arcos.events.QueryTranscribedEvent;
import com.arcos.events.TranscriptionFinishedEvent;
import io.github.givimad.whisperjni.WhisperContext;
import io.github.givimad.whisperjni.WhisperFullParams;
import io.github.givimad.whisperjni.WhisperJNI;

import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Paths;

public class SpeechToText {
    private WhisperJNI whisper;
    private WhisperContext context;
    private WhisperFullParams params;
    private ByteArrayOutputStream audioBuffer;
    private boolean isInitialized = false;

    private final TargetDataLine microphone;
    private final EventBus eventBus;

    // Silence detection parameters
    private static final int SILENCE_THRESHOLD = 1000; // Adjust based on your environment
    private static final long SILENCE_DURATION_MS = 2000; // 2 seconds
    private static final int SAMPLE_RATE = 16000;
    private static final int BYTES_PER_SAMPLE = 2; // 16-bit audio
    private static final int FRAME_SIZE = SAMPLE_RATE * BYTES_PER_SAMPLE / 20; // 50ms frames

    public SpeechToText(String modelPath, TargetDataLine microphone) {
        this.eventBus = EventBus.getInstance();
        this.microphone = microphone;
        try {
            WhisperJNI.loadLibrary();
            WhisperJNI.setLibraryLogger(null);

            this.whisper = new WhisperJNI();
            this.context = whisper.init(Paths.get(modelPath));
            if (this.context == null) {
                throw new RuntimeException("Failed to initialize Whisper context with model: " + modelPath);
            }

            this.params = new WhisperFullParams();
            this.params.printRealtime = false;
            this.params.printProgress = false;
            this.params.printTimestamps = false;
            this.params.printSpecial = false;
            this.params.translate = false;
            this.params.language = "fr";
            this.params.nThreads = Runtime.getRuntime().availableProcessors();
            this.params.offsetMs = 0;
            this.params.durationMs = 0;

            this.audioBuffer = new ByteArrayOutputStream();
            this.isInitialized = true;
            System.out.println("WhisperJNI initialized successfully with French language model");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize WhisperJNI: " + e.getMessage(), e);
        }
    }

    public void listenAndTranscribe() {
        new Thread(() -> {
            ByteBuffer buffer = ByteBuffer.allocate(FRAME_SIZE);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            System.out.println("Started listening for speech with WhisperJNI...");
            reset(); // Reset buffer for new session

            long lastSoundTime = System.currentTimeMillis();
            boolean hasDetectedSpeech = false;
            long recordingStartTime = System.currentTimeMillis();

            try {
                while (!Thread.currentThread().isInterrupted()) {
                    int numBytesRead = this.microphone.read(buffer.array(), 0, FRAME_SIZE);

                    if (numBytesRead > 0) {
                        byte[] audioData = new byte[numBytesRead];
                        System.arraycopy(buffer.array(), 0, audioData, 0, numBytesRead);

                        boolean isSilent = isSilence(audioData);
                        if (!isSilent) {
                            lastSoundTime = System.currentTimeMillis();
                            if (!hasDetectedSpeech) {
                                hasDetectedSpeech = true;
                                System.out.println("Speech detected, starting transcription...");
                            }
                        }

                        processAudio(audioData);

                        if (hasDetectedSpeech && isSilent) {
                            long silenceDuration = System.currentTimeMillis() - lastSoundTime;
                            if (silenceDuration >= SILENCE_DURATION_MS) {
                                System.out.println("Detected " + SILENCE_DURATION_MS + "ms of silence, processing transcription...");
                                break;
                            }
                        }

                        long totalRecordingTime = System.currentTimeMillis() - recordingStartTime;
                        if (totalRecordingTime > 30000) {
                            System.out.println("Maximum recording time reached (30s), processing transcription...");
                            break;
                        }
                    } else {
                        Thread.sleep(10);
                    }
                }
            } catch (InterruptedException e) {
                System.out.println("Audio forwarding interrupted");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                System.err.println("Error in audio forwarding: " + e.getMessage());
                e.printStackTrace();
            } finally {
                String transcription = getTranscription();
                System.out.println("Transcription: " + transcription);
                if (!transcription.isEmpty()) {
                    eventBus.publish(new QueryTranscribedEvent(transcription));
                }
                eventBus.publish(new TranscriptionFinishedEvent());
            }
        }).start();
    }

    private boolean isSilence(byte[] audioData) {
        long sum = 0;
        int sampleCount = audioData.length / 2;

        for (int i = 0; i < audioData.length - 1; i += 2) {
            short sample = (short) ((audioData[i + 1] << 8) | (audioData[i] & 0xFF));
            sum += sample * sample;
        }

        double rms = Math.sqrt((double) sum / sampleCount);
        return rms < SILENCE_THRESHOLD;
    }

    private void processAudio(byte[] audioData) {
        if (!isInitialized) return;
        try {
            this.audioBuffer.write(audioData);
        } catch (IOException e) {
            System.err.println("Error buffering audio data: " + e.getMessage());
        }
    }

    private String getTranscription() {
        if (!isInitialized) return "";
        try {
            byte[] audioBytes = this.audioBuffer.toByteArray();
            if (audioBytes.length == 0) return "";
            float[] samples = convertBytesToFloatSamples(audioBytes);
            if (samples.length == 0) return "";
            System.out.println("Processing " + samples.length + " audio samples with Whisper...");
            int result = whisper.full(context, params, samples, samples.length);
            if (result != 0) {
                System.err.println("Whisper transcription failed with code: " + result);
                return "";
            }
            int numSegments = whisper.fullNSegments(context);
            StringBuilder transcription = new StringBuilder();
            for (int i = 0; i < numSegments; i++) {
                String segmentText = whisper.fullGetSegmentText(context, i);
                if (segmentText != null && !segmentText.trim().isEmpty()) {
                    transcription.append(segmentText.trim()).append(" ");
                }
            }
            return transcription.toString().trim();
        } catch (Exception e) {
            System.err.println("Error during Whisper transcription: " + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }

    private float[] convertBytesToFloatSamples(byte[] audioBytes) {
        int numSamples = audioBytes.length / BYTES_PER_SAMPLE;
        float[] samples = new float[numSamples];
        ByteBuffer buffer = ByteBuffer.wrap(audioBytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < numSamples; i++) {
            if (buffer.remaining() >= BYTES_PER_SAMPLE) {
                short sample = buffer.getShort();
                samples[i] = sample / 32768.0f;
            }
        }
        return samples;
    }

    public void reset() {
        if (this.audioBuffer != null) this.audioBuffer.reset();
        System.out.println("SpeechToText reset for new session");
    }

    public void close() {
        try {
            if (this.context != null) {
                this.context.close();
                this.context = null;
            }
            if (this.audioBuffer != null) {
                this.audioBuffer.close();
                this.audioBuffer = null;
            }
            this.isInitialized = false;
            System.out.println("WhisperJNI resources cleaned up");
        } catch (Exception e) {
            System.err.println("Error closing SpeechToText: " + e.getMessage());
        }
    }
}