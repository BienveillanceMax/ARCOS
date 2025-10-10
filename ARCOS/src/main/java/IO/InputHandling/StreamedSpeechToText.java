package IO.InputHandling;

import io.github.givimad.whisperjni.WhisperContext;
import io.github.givimad.whisperjni.WhisperFullParams;
import io.github.givimad.whisperjni.WhisperJNI;
import io.github.givimad.whisperjni.WhisperState;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Paths;

@Slf4j
public class StreamedSpeechToText implements AutoCloseable {
    private final WhisperJNI whisper;
    private final WhisperContext context;
    private WhisperState state;
    private final WhisperFullParams params;
    private int lastProcessedSegment = 0;

    private static final int BYTES_PER_SAMPLE = 2; // 16-bit

    public StreamedSpeechToText(String modelPath) throws IOException {
        WhisperJNI.loadLibrary();
        whisper = new WhisperJNI();
        context = whisper.init(Paths.get(modelPath));
        if (context == null) {
            throw new IOException("Failed to initialize Whisper context with model: " + modelPath);
        }
        state = whisper.initState(context);
        if (state == null) {
            throw new IOException("Failed to initialize Whisper state");
        }
        params = new WhisperFullParams();
        params.printRealtime = false;
        params.printProgress = false;
        params.printTimestamps = false;
        params.printSpecial = false;
        params.translate = false;
        params.language = "fr";
        params.nThreads = Runtime.getRuntime().availableProcessors();
        params.offsetMs = 0;
        params.durationMs = 0;
    }

    public String transcribe(byte[] audioData) {
        float[] samples = convertBytesToFloatSamples(audioData);
        if (samples.length == 0) {
            return "";
        }

        int result = whisper.fullWithState(context, state, params, samples, samples.length);
        if (result != 0) {
            log.error("Whisper transcription failed with code: {}", result);
            return "";
        }

        int numSegments = whisper.fullNSegmentsFromState(state);
        StringBuilder newText = new StringBuilder();
        for (int i = lastProcessedSegment; i < numSegments; i++) {
            String segmentText = whisper.fullGetSegmentTextFromState(state, i);
            if (segmentText != null && !segmentText.trim().isEmpty()) {
                newText.append(segmentText.trim()).append(" ");
            }
        }
        lastProcessedSegment = numSegments;

        return cleanTranscript(newText.toString().trim());
    }

    private String cleanTranscript(String transcript) {
        if (transcript == null) return null;
        return transcript.replaceAll("\\[.*?\\]", "").trim();
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
        if (state != null) {
            whisper.free(state);
        }
        state = whisper.initState(context);
        lastProcessedSegment = 0;
        log.info("SpeechToText reset for new session");
    }

    @Override
    public void close() {
        if (state != null) {
            whisper.free(state);
            state = null;
        }
        if (context != null) {
            whisper.free(context);
        }
    }
}