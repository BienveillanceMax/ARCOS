package org.arcos.IO.InputHandling.STT;

import org.arcos.Configuration.SpeechToTextProperties;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Facade publique pour la transcription speech-to-text.
 * Gère le buffering audio, la création WAV, le filtrage des hallucinations,
 * et délègue la transcription HTTP à un {@link SttBackend} (adapter pattern).
 */
@Slf4j
public class SttGate {

    private static final int SAMPLE_RATE = 16000;
    private static final int BYTES_PER_SAMPLE = 2; // 16-bit
    private static final int CHANNELS = 1;
    private static final long MINIMUM_AUDIO_DURATION_MS = 500;
    // 120 seconds max — safety valve for RPi memory (120s * 32KB/s = ~3.8 MB)
    private static final int MAX_AUDIO_BYTES = SAMPLE_RATE * BYTES_PER_SAMPLE * CHANNELS * 120;

    private final SttBackend backend;
    private final ByteArrayOutputStream audioBuffer;

    public SttGate(SttBackend backend) {
        this.backend = backend;
        this.audioBuffer = new ByteArrayOutputStream();
        log.info("SttGate initialized with backend: {}", backend.describe());
    }

    public static SttGate create(SttBackendType type, SpeechToTextProperties props) {
        SttBackend backend = switch (type) {
            case FASTER_WHISPER -> new FasterWhisperAdapter(
                    props.getFasterWhisperUrl(),
                    props.getFasterWhisperModel(),
                    props.getLanguage());
            case WHISPER_CPP -> new WhisperCppAdapter(
                    props.getWhisperCppUrl(),
                    props.getLanguage());
        };
        return new SttGate(backend);
    }

    public void processAudio(byte[] audioData) {
        if (audioBuffer.size() + audioData.length > MAX_AUDIO_BYTES) {
            log.warn("Audio buffer capacity reached ({} bytes), dropping frame", MAX_AUDIO_BYTES);
            return;
        }
        audioBuffer.write(audioData, 0, audioData.length);
    }

    public String getTranscription() {
        byte[] audioBytes = audioBuffer.toByteArray();
        if (audioBytes.length == 0) {
            return "";
        }

        byte[] wavData = buildWav(audioBytes);
        log.info("Sending {} bytes of audio data for transcription...", audioBytes.length);
        String rawTranscript = backend.transcribe(wavData);
        return cleanTranscript(rawTranscript);
    }

    public void reset() {
        audioBuffer.reset();
        log.info("SttGate reset for new session");
    }

    public long getMinimumAudioDurationMs() {
        return MINIMUM_AUDIO_DURATION_MS;
    }

    public long getBufferedAudioDurationMs() {
        int audioBytes = audioBuffer.size();
        int numSamples = audioBytes / BYTES_PER_SAMPLE;
        return (long) ((double) numSamples / SAMPLE_RATE * 1000);
    }

    public boolean hasMinimumAudio() {
        return getBufferedAudioDurationMs() >= MINIMUM_AUDIO_DURATION_MS;
    }

    public void close() {
        try {
            audioBuffer.close();
        } catch (IOException e) {
            log.error("Error closing audio buffer", e);
        }
        backend.close();
        log.info("SttGate resources cleaned up");
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private byte[] buildWav(byte[] pcmData) {
        byte[] wav = new byte[44 + pcmData.length];
        writeWavHeader(wav, pcmData.length);
        System.arraycopy(pcmData, 0, wav, 44, pcmData.length);
        return wav;
    }

    private void writeWavHeader(byte[] header, int pcmDataLength) {
        int totalDataLen = pcmDataLength + 36;
        long byteRate = (long) BYTES_PER_SAMPLE * SAMPLE_RATE * CHANNELS;

        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0;
        header[20] = 1; header[21] = 0; // PCM
        header[22] = (byte) CHANNELS; header[23] = 0;
        header[24] = (byte) (SAMPLE_RATE & 0xff);
        header[25] = (byte) ((SAMPLE_RATE >> 8) & 0xff);
        header[26] = (byte) ((SAMPLE_RATE >> 16) & 0xff);
        header[27] = (byte) ((SAMPLE_RATE >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (CHANNELS * BYTES_PER_SAMPLE); header[33] = 0;
        header[34] = 16; header[35] = 0; // bits per sample
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        header[40] = (byte) (pcmDataLength & 0xff);
        header[41] = (byte) ((pcmDataLength >> 8) & 0xff);
        header[42] = (byte) ((pcmDataLength >> 16) & 0xff);
        header[43] = (byte) ((pcmDataLength >> 24) & 0xff);
    }

    private static final List<String> HALLUCINATION_PATTERNS = List.of(
            "sous-titrage",
            "sous-titres",
            "sous-titre",
            "amara.org",
            "merci d'avoir regardé",
            "abonnez-vous",
            "n'hésitez pas à",
            "likez cette vidéo",
            "s'il vous plaît",
            "merci pour votre attention"
    );

    private String cleanTranscript(String transcript) {
        if (transcript == null) return null;
        String cleaned = transcript.replaceAll("\\[.*?\\]", "").trim();
        String lower = cleaned.toLowerCase();
        for (String pattern : HALLUCINATION_PATTERNS) {
            if (lower.contains(pattern)) {
                log.debug("Filtered Whisper hallucination: '{}'", cleaned);
                return "";
            }
        }
        return cleaned;
    }
}
