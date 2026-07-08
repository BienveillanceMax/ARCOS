package org.arcos.IO.InputHandling.STT;

import okhttp3.MultipartBody;

/**
 * Adapter pour le serveur whisper.cpp.
 * POST {baseUrl}/inference
 */
public class WhisperCppAdapter extends AbstractSttAdapter {

    /**
     * Bornes du contexte mel de l'encodeur (audio_ctx), calibrées par SttAccuracyBench
     * (2026-07-02) : sous ~700, whisper hallucine même sur de l'audio court (WER 43-88%) ;
     * 750 (15s) donne un WER identique au défaut 1500 pour ~2x moins de latence.
     */
    static final int AUDIO_CTX_FLOOR = 750;
    static final int AUDIO_CTX_MAX = 1500;
    /** Trames mel encodeur par seconde d'audio. */
    private static final double MEL_FRAMES_PER_SECOND = 50.0;
    private static final int AUDIO_CTX_MARGIN = 32;
    /** Débit du PCM 16kHz mono 16-bit ; le WAV a un header de 44 octets. */
    private static final double WAV_BYTES_PER_SECOND = 32_000.0;
    private static final int WAV_HEADER_BYTES = 44;

    WhisperCppAdapter(String baseUrl, String language, long timeoutMs) {
        super(baseUrl, language, timeoutMs);
    }

    @Override
    protected String endpointPath() {
        return "/inference";
    }

    @Override
    protected MultipartBody.Builder addFormFields(MultipartBody.Builder builder, byte[] wavData) {
        return builder
                .addFormDataPart("temperature", "0")
                .addFormDataPart("response_format", "json")
                .addFormDataPart("language", language)
                // Contexte encodeur ajusté à la durée réelle : latence minimale sans troncature
                // (le serveur whisper.cpp honore audio_ctx par requête — vérifié 2026-07-02).
                .addFormDataPart("audio_ctx", String.valueOf(dynamicAudioCtx(wavData)));
    }

    /** audio_ctx couvrant la durée du WAV + marge, clampé [{@value #AUDIO_CTX_FLOOR}, {@value #AUDIO_CTX_MAX}]. */
    public static int dynamicAudioCtx(byte[] wavData) {
        double durationSec = Math.max(0, wavData.length - WAV_HEADER_BYTES) / WAV_BYTES_PER_SECOND;
        int ctx = (int) Math.ceil(durationSec * MEL_FRAMES_PER_SECOND) + AUDIO_CTX_MARGIN;
        return Math.max(AUDIO_CTX_FLOOR, Math.min(AUDIO_CTX_MAX, ctx));
    }

    @Override
    public String describe() {
        return "whisper.cpp @ " + baseUrl;
    }
}
