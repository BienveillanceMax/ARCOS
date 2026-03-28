package org.arcos.IO.InputHandling.STT;

/**
 * Adapter interne pour les backends de transcription speech-to-text.
 * Chaque implémentation gère l'appel HTTP et le parsing de la réponse
 * pour un service STT spécifique.
 */
interface SttBackend {

    /**
     * Envoie des données WAV au service de transcription et retourne le texte brut.
     *
     * @param wavData contenu WAV complet (header + PCM)
     * @return texte transcrit, ou chaîne vide en cas d'erreur
     */
    String transcribe(byte[] wavData);

    /**
     * Description lisible pour les logs (ex: "faster-whisper @ localhost:8000").
     */
    String describe();

    /**
     * Libère les ressources (client HTTP, etc.).
     */
    void close();
}
