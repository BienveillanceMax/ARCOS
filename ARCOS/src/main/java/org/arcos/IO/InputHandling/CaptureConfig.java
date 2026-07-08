package org.arcos.IO.InputHandling;

import org.arcos.Configuration.AudioProperties;

/**
 * Paramètres d'une session de capture d'énoncé — les seules vraies différences entre
 * l'écoute post-wake-word et la fenêtre de conversation multi-tours.
 *
 * @param label                 préfixe de log ("" ou "[CONVERSATION] ")
 * @param initialListenWindowMs délai max d'attente du début de parole avant abandon
 * @param silenceDurationMs     durée de silence continu qui clôt l'énoncé
 * @param maxRecordingMs        durée max d'enregistrement une fois la parole détectée
 */
public record CaptureConfig(String label,
                            long initialListenWindowMs,
                            long silenceDurationMs,
                            long maxRecordingMs) {

    /** Écoute déclenchée par le wake word. */
    public static CaptureConfig forWake(AudioProperties audio) {
        return new CaptureConfig("",
                audio.getPostResponseListeningWindowMs(),
                audio.getSilenceDurationMs(),
                audio.getMaxRecordingSeconds() * 1000L);
    }

    /** Fenêtre de conversation post-réponse (sans wake word), bornée par le temps restant. */
    public static CaptureConfig forConversation(AudioProperties audio, long remainingWindowMs) {
        return forConversation(audio, remainingWindowMs, audio.getConversationSilenceMs());
    }

    /** Variante avec silence contextuel (EndpointingPolicyService) au lieu de la valeur de config. */
    public static CaptureConfig forConversation(AudioProperties audio, long remainingWindowMs, long silenceDurationMs) {
        return new CaptureConfig("[CONVERSATION] ",
                remainingWindowMs,
                silenceDurationMs,
                audio.getMaxRecordingSeconds() * 1000L);
    }
}
