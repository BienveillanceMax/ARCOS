package org.arcos.IO.InputHandling.STT;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;

/**
 * Base partagée pour les adapters STT. Gère le client HTTP, le parsing JSON,
 * et le cycle de vie. Les sous-classes fournissent uniquement le chemin
 * d'endpoint et la construction du body multipart.
 */
@Slf4j
abstract class AbstractSttAdapter implements SttBackend {

    private static final MediaType WAV_TYPE = MediaType.parse("audio/wav");
    private static final long CONNECT_TIMEOUT_MS = 3_000;

    protected final String baseUrl;
    protected final String language;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    AbstractSttAdapter(String baseUrl, String language, long timeoutMs) {
        this.baseUrl = baseUrl;
        this.language = language;
        this.httpClient = new OkHttpClient.Builder()
                .callTimeout(Duration.ofMillis(timeoutMs))
                .readTimeout(Duration.ofMillis(timeoutMs))
                .connectTimeout(Duration.ofMillis(Math.min(CONNECT_TIMEOUT_MS, timeoutMs)))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    protected abstract String endpointPath();

    /** Champs multipart propres au backend. {@code wavData} permet les champs dépendant de l'audio (ex. audio_ctx). */
    protected abstract MultipartBody.Builder addFormFields(MultipartBody.Builder builder, byte[] wavData);

    @Override
    public SttCall newCall(byte[] wavData) {
        MultipartBody.Builder bodyBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "audio.wav",
                        RequestBody.create(wavData, WAV_TYPE));

        RequestBody requestBody = addFormFields(bodyBuilder, wavData).build();

        Request request = new Request.Builder()
                .url(baseUrl + endpointPath())
                .post(requestBody)
                .build();

        Call call = httpClient.newCall(request);
        return new SttCall() {
            @Override
            public SttResult await() {
                try (Response response = call.execute()) {
                    if (!response.isSuccessful()) {
                        log.error("Transcription failed: code={}, body={}", response.code(),
                                response.body() != null ? response.body().string() : "null");
                        return SttResult.error();
                    }

                    String responseBody = Objects.requireNonNull(response.body()).string();
                    JsonNode json = objectMapper.readTree(responseBody);
                    return SttResult.transcript(json.path("text").asText(""));
                } catch (IOException e) {
                    if (call.isCanceled()) {
                        log.debug("{} transcription cancelled", describe());
                    } else {
                        log.error("Error during {} transcription", describe(), e);
                    }
                    return SttResult.error();
                }
            }

            @Override
            public void cancel() {
                call.cancel();
            }
        };
    }

    @Override
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
