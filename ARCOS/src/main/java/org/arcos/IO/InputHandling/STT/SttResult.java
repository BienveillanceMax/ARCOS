package org.arcos.IO.InputHandling.STT;

/**
 * Résultat typé d'une capture/transcription. Remplace le "" fourre-tout : un backend en
 * panne, une fenêtre sans parole et un énoncé incompréhensible appellent des réactions
 * différentes (silence, timeout de fenêtre, récupération conversationnelle, message d'erreur).
 */
public record SttResult(Status status, String text) {

    public enum Status {
        /** Parole transcrite avec succès — {@link #text} est non vide. */
        TRANSCRIPT,
        /** Aucune parole détectée (fenêtre expirée, pas assez d'audio). */
        NO_SPEECH,
        /** Parole détectée mais transcription vide (marmonnement, hallucination filtrée). */
        UNINTELLIGIBLE,
        /** Échec du backend STT (HTTP non-2xx, I/O, timeout). */
        ERROR
    }

    public static SttResult transcript(String text) {
        return new SttResult(Status.TRANSCRIPT, text);
    }

    public static SttResult noSpeech() {
        return new SttResult(Status.NO_SPEECH, "");
    }

    public static SttResult unintelligible() {
        return new SttResult(Status.UNINTELLIGIBLE, "");
    }

    public static SttResult error() {
        return new SttResult(Status.ERROR, "");
    }

    public boolean hasTranscript() {
        return status == Status.TRANSCRIPT && text != null && !text.isBlank();
    }
}
