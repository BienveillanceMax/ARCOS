package EventLoop.InputHandling;

import io.github.givimad.whisperjni.WhisperJNI;
import io.github.givimad.whisperjni.WhisperContext;
import io.github.givimad.whisperjni.WhisperFullParams;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SpeechToText {
    private WhisperJNI whisper;
    private WhisperContext context;
    private WhisperFullParams params;

    // Audio parameters - Whisper expects 16kHz mono PCM
    private static final int SAMPLE_RATE = 16000;
    private static final int BYTES_PER_SAMPLE = 2; // 16-bit

    // Buffer to accumulate audio data
    private ByteArrayOutputStream audioBuffer;
    private boolean isInitialized = false;

    public SpeechToText(String modelPath) {
        try {
            // Load WhisperJNI library
            WhisperJNI.loadLibrary();
            WhisperJNI.setLibraryLogger(null); // Disable whisper.cpp logging

            this.whisper = new WhisperJNI();

            // Initialize Whisper context with the model
            Path modelFile = Paths.get(modelPath);
            this.context = whisper.init(modelFile);

            if (this.context == null) {
                throw new RuntimeException("Failed to initialize Whisper context with model: " + modelPath);
            }

            // Configure Whisper parameters
            this.params = new WhisperFullParams();
            this.params.printRealtime = false;
            this.params.printProgress = false;
            this.params.printTimestamps = false;
            this.params.printSpecial = false;
            this.params.translate = false; // Set to true if you want English translation
            this.params.language = "fr"; // French language
            this.params.nThreads = Runtime.getRuntime().availableProcessors();
            this.params.offsetMs = 0;
            this.params.durationMs = 0; // Process entire audio

            // Initialize audio buffer
            this.audioBuffer = new ByteArrayOutputStream();
            this.isInitialized = true;

            System.out.println("WhisperJNI initialized successfully with French language model");

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize WhisperJNI: " + e.getMessage(), e);
        }
    }

    /**
     * Process incoming audio data by buffering it
     */
    public void processAudio(byte[] audioData) {
        if (!isInitialized) {
            System.err.println("SpeechToText not initialized");
            return;
        }

        try {
            // Buffer the audio data
            this.audioBuffer.write(audioData);
        } catch (IOException e) {
            System.err.println("Error buffering audio data: " + e.getMessage());
        }
    }

    /**
     * Transcribe all buffered audio and return the result
     */
    public String getTranscription() {
        if (!isInitialized) {
            System.err.println("SpeechToText not initialized");
            return "";
        }

        try {
            byte[] audioBytes = this.audioBuffer.toByteArray();

            if (audioBytes.length == 0) {
                return "";
            }

            // Convert byte array to float array (Whisper expects float samples)
            float[] samples = convertBytesToFloatSamples(audioBytes);

            if (samples.length == 0) {
                return "";
            }

            System.out.println("Processing " + samples.length + " audio samples with Whisper...");

            // Run Whisper transcription
            int result = whisper.full(context, params, samples, samples.length);

            if (result != 0) {
                System.err.println("Whisper transcription failed with code: " + result);
                return "";
            }

            // Extract transcribed text from all segments
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

    /**
     * Convert 16-bit PCM byte array to float samples
     * Whisper expects float samples in range [-1.0, 1.0]
     */
    private float[] convertBytesToFloatSamples(byte[] audioBytes) {
        int numSamples = audioBytes.length / BYTES_PER_SAMPLE;
        float[] samples = new float[numSamples];

        ByteBuffer buffer = ByteBuffer.wrap(audioBytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < numSamples; i++) {
            if (buffer.remaining() >= BYTES_PER_SAMPLE) {
                short sample = buffer.getShort();
                // Convert to float in range [-1.0, 1.0]
                samples[i] = sample / 32768.0f;
            }
        }

        return samples;
    }

    /**
     * Reset the audio buffer for a new transcription session
     */
    public void reset() {
        if (this.audioBuffer != null) {
            this.audioBuffer.reset();
        }
        System.out.println("SpeechToText reset for new session");
    }

    /**
     * Get the minimum audio duration (in milliseconds) needed for reasonable transcription
     */
    public long getMinimumAudioDurationMs() {
        return 500; // 0.5 seconds minimum
    }

    /**
     * Get current buffered audio duration in milliseconds
     */
    public long getBufferedAudioDurationMs() {
        if (audioBuffer == null) return 0;

        int audioBytes = audioBuffer.size();
        int numSamples = audioBytes / BYTES_PER_SAMPLE;
        return (long) ((double) numSamples / SAMPLE_RATE * 1000);
    }

    /**
     * Check if we have enough audio data for transcription
     */
    public boolean hasMinimumAudio() {
        return getBufferedAudioDurationMs() >= getMinimumAudioDurationMs();
    }

    /**
     * Close and cleanup resources
     */
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