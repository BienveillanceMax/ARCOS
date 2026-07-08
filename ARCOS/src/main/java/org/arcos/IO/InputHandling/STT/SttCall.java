package org.arcos.IO.InputHandling.STT;

/**
 * Transcription STT en vol, annulable.
 *
 * Permet au STT spéculatif d'abandonner proprement un appel orphelin (reprise de parole) :
 * {@link #cancel()} interrompt le round-trip HTTP côté client, libérant immédiatement le
 * thread de spéculation au lieu de le laisser bloqué derrière une requête dont le résultat
 * sera jeté.
 */
public interface SttCall {

    /** Exécute (ou attend) la transcription. Bloquant. {@link SttResult#error()} en cas d'échec/annulation. */
    SttResult await();

    /** Abandon best-effort du round-trip HTTP en vol. Idempotent. */
    void cancel();

    /** Appel déjà résolu — utilisé quand il n'y a rien à transcrire (buffer vide). */
    static SttCall completed(SttResult result) {
        return new SttCall() {
            @Override public SttResult await() { return result; }
            @Override public void cancel() { }
        };
    }
}
