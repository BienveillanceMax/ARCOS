package org.arcos.IO.InputHandling;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SpeechToText {

    private static final int SAMPLE_RATE = 16000;
    private static final int BYTES_PER_SAMPLE = 2; // 16-bit
    private static final int CHANNELS = 1; // Mono

    private final String remoteUrl;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final ByteArrayOutputStream audioBuffer;
    private boolean isInitialized = false;

    public SpeechToText(String remoteUrl) {
        this.remoteUrl = remoteUrl;
        this.httpClient = new OkHttpClient.Builder()
                .callTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
        this.audioBuffer = new ByteArrayOutputStream();
        this.isInitialized = true;
        log.info("SpeechToText initialized to use remote server at: {}", remoteUrl);
    }

    public void processAudio(byte[] audioData) {
        if (!isInitialized) {
            log.error("SpeechToText not initialized");
            return;
        }
        try {
            this.audioBuffer.write(audioData);
        } catch (IOException e) {
            log.error("Error buffering audio data", e);
        }
    }

    public String getTranscription() {
        if (!isInitialized) {
            log.error("SpeechToText not initialized");
            return "";
        }

        byte[] audioBytes = this.audioBuffer.toByteArray();
        if (audioBytes.length == 0) {
            return "";
        }

        File tempWavFile = null;
        try {
            // Create a temporary WAV file
            tempWavFile = createTempWavFile(audioBytes);

            // Build the request
            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("audio_file", tempWavFile.getName(),
                            RequestBody.create(tempWavFile, MediaType.parse("audio/wav")))
                    .build();

            Request request = new Request.Builder()
                    .url(this.remoteUrl + "/api/v0/transcribe")
                    .post(requestBody)
                    .build();

            log.info("Sending {} bytes of audio data for transcription...", audioBytes.length);

            // Execute the request
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("Transcription request failed with code: {} and message: {}", response.code(), response.body() != null ? response.body().string() : "null");
                    return "";
                }

                String responseBody = Objects.requireNonNull(response.body()).string();
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                String transcript = jsonResponse.has("text") ? jsonResponse.get("text").getAsString() : "";
                return cleanTranscript(transcript);
            }
        } catch (IOException e) {
            log.error("Error during transcription request", e);
            return "";
        } finally {
            if (tempWavFile != null) {
                if (!tempWavFile.delete()) {
                    log.warn("Failed to delete temporary WAV file: {}", tempWavFile.getAbsolutePath());
                }
            }
        }
    }

    private File createTempWavFile(byte[] pcmData) throws IOException {
        File tempFile = File.createTempFile("speech", ".wav");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            writeWavHeader(fos, pcmData.length);
            fos.write(pcmData);
        }
        return tempFile;
    }

    private void writeWavHeader(FileOutputStream fos, int pcmDataLength) throws IOException {
        int totalDataLen = pcmDataLength + 36;
        long longSampleRate = SAMPLE_RATE;
        int channels = CHANNELS;
        long byteRate = BYTES_PER_SAMPLE * SAMPLE_RATE * channels;

        byte[] header = new byte[44];
        header[0] = 'R'; // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f'; // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16; // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1; // format = 1 (PCM)
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (channels * BYTES_PER_SAMPLE); // block align
        header[33] = 0;
        header[34] = 16; // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (pcmDataLength & 0xff);
        header[41] = (byte) ((pcmDataLength >> 8) & 0xff);
        header[42] = (byte) ((pcmDataLength >> 16) & 0xff);
        header[43] = (byte) ((pcmDataLength >> 24) & 0xff);

        fos.write(header, 0, 44);
    }

    private String cleanTranscript(String transcript) {
        if (transcript == null) return null;
        return transcript.replaceAll("\\[.*?\\]", "").trim();
    }

    public void reset() {
        if (this.audioBuffer != null) {
            this.audioBuffer.reset();
        }
        log.info("SpeechToText reset for new session");
    }

    public long getMinimumAudioDurationMs() {
        return 500; // 0.5 seconds minimum
    }

    public long getBufferedAudioDurationMs() {
        if (audioBuffer == null) return 0;
        int audioBytes = audioBuffer.size();
        int numSamples = audioBytes / BYTES_PER_SAMPLE;
        return (long) ((double) numSamples / SAMPLE_RATE * 1000);
    }

    public boolean hasMinimumAudio() {
        return getBufferedAudioDurationMs() >= getMinimumAudioDurationMs();
    }

    public void close() {
        try {
            if (this.audioBuffer != null) {
                this.audioBuffer.close();
            }
            this.isInitialized = false;
            log.info("SpeechToText resources cleaned up");
        } catch (IOException e) {
            log.error("Error closing SpeechToText", e);
        }
    }
}
