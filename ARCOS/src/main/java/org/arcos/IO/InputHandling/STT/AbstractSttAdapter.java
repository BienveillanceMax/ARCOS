package org.arcos.IO.InputHandling.STT;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Base partagée pour les adapters STT. Gère le client HTTP, le parsing JSON,
 * et le cycle de vie. Les sous-classes fournissent uniquement le chemin
 * d'endpoint et la construction du body multipart.
 */
@Slf4j
abstract class AbstractSttAdapter implements SttBackend {

    private static final MediaType WAV_TYPE = MediaType.parse("audio/wav");

    protected final String baseUrl;
    protected final String language;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    AbstractSttAdapter(String baseUrl, String language) {
        this.baseUrl = baseUrl;
        this.language = language;
        this.httpClient = new OkHttpClient.Builder()
                .callTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    protected abstract String endpointPath();

    protected abstract MultipartBody.Builder addFormFields(MultipartBody.Builder builder);

    @Override
    public String transcribe(byte[] wavData) {
        MultipartBody.Builder bodyBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "audio.wav",
                        RequestBody.create(wavData, WAV_TYPE));

        RequestBody requestBody = addFormFields(bodyBuilder).build();

        Request request = new Request.Builder()
                .url(baseUrl + endpointPath())
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("Transcription failed: code={}, body={}", response.code(),
                        response.body() != null ? response.body().string() : "null");
                return "";
            }

            String responseBody = Objects.requireNonNull(response.body()).string();
            JsonNode json = objectMapper.readTree(responseBody);
            return json.path("text").asText("");
        } catch (IOException e) {
            log.error("Error during {} transcription", describe(), e);
            return "";
        }
    }

    @Override
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
