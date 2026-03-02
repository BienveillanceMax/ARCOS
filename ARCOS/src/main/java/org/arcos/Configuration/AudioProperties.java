package org.arcos.Configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propriétés audio externalisées depuis application.properties.
 * Remplace toutes les constantes hardcodées dans WakeWordProducer.
 *
 * Préfixe : arcos.audio
 */
@Component
@ConfigurationProperties(prefix = "arcos.audio")
public class AudioProperties {

    /** Index du device audio d'entrée (microphone). -1 = auto. */
    private int inputDeviceIndex = -1;

    /** Fréquence d'échantillonnage du microphone en Hz. */
    private int sampleRate = 44100;

    /** Seuil RMS en-dessous duquel l'audio est considéré comme silence. */
    private int silenceThreshold = 1000;

    /** Durée de silence en ms avant d'arrêter l'enregistrement. */
    private int silenceDurationMs = 1200;

    /** Durée maximale d'enregistrement en secondes. */
    private int maxRecordingSeconds = 30;

    /** Active la fenêtre d'écoute post-réponse (mode conversation multi-tours). */
    private boolean multiTurnEnabled = true;

    /** Durée de la fenêtre d'écoute post-réponse en ms. */
    private int postResponseListeningWindowMs = 4000;

    /** Durée de silence avant fermeture anticipée de la fenêtre de conversation en ms. */
    private int conversationSilenceMs = 1500;

    public int getInputDeviceIndex() {
        return inputDeviceIndex;
    }

    public void setInputDeviceIndex(int inputDeviceIndex) {
        this.inputDeviceIndex = inputDeviceIndex;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    public int getSilenceThreshold() {
        return silenceThreshold;
    }

    public void setSilenceThreshold(int silenceThreshold) {
        this.silenceThreshold = silenceThreshold;
    }

    public int getSilenceDurationMs() {
        return silenceDurationMs;
    }

    public void setSilenceDurationMs(int silenceDurationMs) {
        this.silenceDurationMs = silenceDurationMs;
    }

    public int getMaxRecordingSeconds() {
        return maxRecordingSeconds;
    }

    public void setMaxRecordingSeconds(int maxRecordingSeconds) {
        this.maxRecordingSeconds = maxRecordingSeconds;
    }

    public boolean isMultiTurnEnabled() {
        return multiTurnEnabled;
    }

    public void setMultiTurnEnabled(boolean multiTurnEnabled) {
        this.multiTurnEnabled = multiTurnEnabled;
    }

    public int getPostResponseListeningWindowMs() {
        return postResponseListeningWindowMs;
    }

    public void setPostResponseListeningWindowMs(int postResponseListeningWindowMs) {
        this.postResponseListeningWindowMs = postResponseListeningWindowMs;
    }

    public int getConversationSilenceMs() {
        return conversationSilenceMs;
    }

    public void setConversationSilenceMs(int conversationSilenceMs) {
        this.conversationSilenceMs = conversationSilenceMs;
    }
}
