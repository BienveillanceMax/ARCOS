package org.arcos.IO.InputHandling.STT;

import okhttp3.MultipartBody;

/**
 * Adapter pour le serveur whisper.cpp.
 * POST {baseUrl}/inference
 */
class WhisperCppAdapter extends AbstractSttAdapter {

    WhisperCppAdapter(String baseUrl, String language) {
        super(baseUrl, language);
    }

    @Override
    protected String endpointPath() {
        return "/inference";
    }

    @Override
    protected MultipartBody.Builder addFormFields(MultipartBody.Builder builder) {
        return builder
                .addFormDataPart("temperature", "0")
                .addFormDataPart("response_format", "json")
                .addFormDataPart("language", language);
    }

    @Override
    public String describe() {
        return "whisper.cpp @ " + baseUrl;
    }
}
