package org.arcos.Configuration;

import org.arcos.IO.InputHandling.STT.SttBackendType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propriétés de configuration speech-to-text externalisées depuis application.properties.
 *
 * Préfixe : arcos.stt
 */
@Component
@ConfigurationProperties(prefix = "arcos.stt")
public class SpeechToTextProperties {

    /** Backend STT actif. */
    private SttBackendType backend = SttBackendType.FASTER_WHISPER;

    /** URL de base du service faster-whisper (API compatible OpenAI). */
    private String fasterWhisperUrl = "http://localhost:8000";

    /** URL de base du serveur whisper.cpp. */
    private String whisperCppUrl = "http://localhost:8090";

    /** Nom du modèle pour faster-whisper. */
    private String fasterWhisperModel = "deepdml/faster-whisper-large-v3-turbo-ct2";

    /** Code langue ISO 639-1. */
    private String language = "fr";

    public SttBackendType getBackend() { return backend; }
    public void setBackend(SttBackendType backend) { this.backend = backend; }

    public String getFasterWhisperUrl() { return fasterWhisperUrl; }
    public void setFasterWhisperUrl(String fasterWhisperUrl) { this.fasterWhisperUrl = fasterWhisperUrl; }

    public String getWhisperCppUrl() { return whisperCppUrl; }
    public void setWhisperCppUrl(String whisperCppUrl) { this.whisperCppUrl = whisperCppUrl; }

    public String getFasterWhisperModel() { return fasterWhisperModel; }
    public void setFasterWhisperModel(String fasterWhisperModel) { this.fasterWhisperModel = fasterWhisperModel; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
}
