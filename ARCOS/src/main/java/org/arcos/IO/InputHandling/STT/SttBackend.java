package org.arcos.IO.InputHandling.STT;

/**
 * Adapter interne pour les backends de transcription speech-to-text.
 * Chaque implémentation gère l'appel HTTP et le parsing de la réponse
 * pour un service STT spécifique.
 */
interface SttBackend {

    /**
     * Prépare une transcription annulable sans l'exécuter. L'appel HTTP part au premier
     * {@link SttCall#await()} ; {@link SttCall#cancel()} interrompt le round-trip en vol.
     *
     * @param wavData contenu WAV complet (header + PCM)
     */
    SttCall newCall(byte[] wavData);

    /**
     * Description lisible pour les logs (ex: "faster-whisper @ localhost:8000").
     */
    String describe();

    /**
     * Libère les ressources (client HTTP, etc.).
     */
    void close();
}
