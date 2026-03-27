package org.arcos.Setup;

import java.util.HashMap;
import java.util.Map;

/**
 * POJO représentant les valeurs de configuration collectées pendant le wizard.
 * Aucune dépendance Spring — plain Java uniquement.
 */
public class ConfigurationModel {

    // ── Clés API ──────────────────────────────────────────────────────────────
    private String mistralApiKey;         // obligatoire
    private String braveSearchApiKey;     // optionnel
    private String porcupineAccessKey;    // optionnel

    // ── Audio ─────────────────────────────────────────────────────────────────
    private int audioDeviceIndex = -1;    // -1 = non configuré / auto
    private String audioDeviceName = "";  // Nom stable du device (résolu au runtime)

    // ── Personnalité ──────────────────────────────────────────────────────────
    private String personalityProfile = "DEFAULT";

    // ── STT Backend ───────────────────────────────────────────────────────────
    private String sttBackend = "FASTER_WHISPER";
    private String sttWhisperCppUrl = "http://localhost:8080";

    // ── Statuts de validation ─────────────────────────────────────────────────
    private final Map<String, Boolean> keyValidationStatus = new HashMap<>();

    // ── Métadonnées ───────────────────────────────────────────────────────────
    /** true si les valeurs ont été chargées depuis un .env existant. */
    private boolean loadedFromExistingEnv = false;

    // ── Getters / Setters ────────────────────────────────────────────────────

    public String getMistralApiKey() { return mistralApiKey; }
    public void setMistralApiKey(String mistralApiKey) { this.mistralApiKey = mistralApiKey; }

    public String getBraveSearchApiKey() { return braveSearchApiKey; }
    public void setBraveSearchApiKey(String braveSearchApiKey) { this.braveSearchApiKey = braveSearchApiKey; }

    public String getPorcupineAccessKey() { return porcupineAccessKey; }
    public void setPorcupineAccessKey(String porcupineAccessKey) { this.porcupineAccessKey = porcupineAccessKey; }

    public int getAudioDeviceIndex() { return audioDeviceIndex; }
    public void setAudioDeviceIndex(int audioDeviceIndex) { this.audioDeviceIndex = audioDeviceIndex; }

    public String getAudioDeviceName() { return audioDeviceName; }
    public void setAudioDeviceName(String audioDeviceName) { this.audioDeviceName = audioDeviceName; }

    public String getPersonalityProfile() { return personalityProfile; }
    public void setPersonalityProfile(String personalityProfile) { this.personalityProfile = personalityProfile; }

    public String getSttBackend() { return sttBackend; }
    public void setSttBackend(String sttBackend) { this.sttBackend = sttBackend; }

    public String getSttWhisperCppUrl() { return sttWhisperCppUrl; }
    public void setSttWhisperCppUrl(String sttWhisperCppUrl) { this.sttWhisperCppUrl = sttWhisperCppUrl; }

    public void setKeyValidated(String keyName, boolean valid) { keyValidationStatus.put(keyName, valid); }
    public Boolean isKeyValidated(String keyName) { return keyValidationStatus.get(keyName); }

    public boolean isLoadedFromExistingEnv() { return loadedFromExistingEnv; }
    public void setLoadedFromExistingEnv(boolean loadedFromExistingEnv) {
        this.loadedFromExistingEnv = loadedFromExistingEnv;
    }

    /** Vérifie que la configuration minimale obligatoire est présente. */
    public boolean isMinimallyComplete() {
        return mistralApiKey != null && !mistralApiKey.isBlank();
    }
}
