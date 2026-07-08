package org.arcos.IO.OuputHandling;

/**
 * Synthèse vocale asynchrone d'ARCOS — interface minimale consommée par l'Orchestrator
 * (et substituée par MockTTSCapture dans les tests E2E).
 *
 * Contrat d'ordre : les textes sont synthétisés et lus en FIFO ; les callbacks
 * {@code onComplete}/{@code afterPlayback} s'exécutent après la fin de lecture audible
 * de tout ce qui précède. {@link #cancelAll()} abandonne la file et coupe la lecture en
 * fondu — les callbacks des éléments abandonnés ne sont PAS invoqués (l'appelant du
 * barge-in reprend la main explicitement).
 */
public interface TTSModule {

    void speakAsync(String text);

    void speakAsync(String text, Runnable onComplete);

    void speakAsync(String text, float lengthScale, float noiseScale, float noiseW);

    void speakAsync(String text, float lengthScale, float noiseScale, float noiseW, Runnable onComplete);

    /** Enfile un callback exécuté après la fin de lecture de tous les audios en attente. */
    void afterPlayback(Runnable callback);

    /** Vide la file (synthèses et callbacks en attente) et coupe la lecture en cours en fondu (~200ms). */
    void cancelAll();

    /** Vrai si de l'audio est en cours de lecture ou en attente. */
    boolean isSpeaking();

    /** Notifié au démarrage de lecture de chaque chunk (télémétrie premier-audio). */
    void setPlaybackStartListener(Runnable listener);
}
