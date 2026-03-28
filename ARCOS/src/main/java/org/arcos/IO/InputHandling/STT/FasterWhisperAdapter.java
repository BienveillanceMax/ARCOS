package org.arcos.IO.InputHandling.STT;

import okhttp3.MultipartBody;

/**
 * Adapter pour le service faster-whisper (API compatible OpenAI).
 * POST {baseUrl}/v1/audio/transcriptions
 */
class FasterWhisperAdapter extends AbstractSttAdapter {

    private final String model;

    FasterWhisperAdapter(String baseUrl, String model, String language) {
        super(baseUrl, language);
        this.model = model;
    }

    @Override
    protected String endpointPath() {
        return "/v1/audio/transcriptions";
    }

    @Override
    protected MultipartBody.Builder addFormFields(MultipartBody.Builder builder) {
        return builder
                .addFormDataPart("model", model)
                .addFormDataPart("language", language);
    }

    @Override
    public String describe() {
        return "faster-whisper @ " + baseUrl;
    }
}
