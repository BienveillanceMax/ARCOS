package org.arcos.IO.Telemetry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Chronologie de latence d'un tour de parole (fin de parole utilisateur → premier audio ARCOS).
 *
 * Un seul tour est actif à la fois (capture mono-thread + event loop mono-thread), donc un
 * singleton suffit. Les marques arrivent depuis des threads différents (capture, event loop,
 * Reactor, playback) — les méthodes sont synchronized, le coût (~6 appels/tour) est négligeable.
 *
 * Émet une ligne parsable par tour au moment du premier audio :
 *   VOICE_TIMELINE eou_ms=.. prompt_ms=.. ttft_ms=.. tts_ms=.. total_ms=..
 *
 *   eou_ms    : dernière trame de parole → transcript STT disponible
 *   prompt_ms : transcript → prompt construit (RAG inclus)
 *   ttft_ms   : prompt construit → premier token LLM
 *   tts_ms    : premier token → début de lecture du premier chunk audio
 *   total_ms  : dernière trame de parole → premier audio (latence perçue)
 *
 * Une marque manquante vaut -1. Un tour jamais complété (STT vide, erreur LLM, barge-in futur)
 * est abandonné en DEBUG au tour suivant.
 */
@Slf4j
@Component
public class TurnTimeline {

    private boolean active;
    private long speechEnd;
    private long sttDone;
    private long promptBuilt;
    private long firstToken;
    private long firstAudible;

    /** Nouveau tour : la parole utilisateur vient d'être détectée. */
    public synchronized void beginTurn() {
        if (active) {
            log.debug("VOICE_TIMELINE tour précédent abandonné avant premier audio ({})", partial());
        }
        active = true;
        speechEnd = sttDone = promptBuilt = firstToken = firstAudible = 0;
    }

    /** Dernière trame non silencieuse (fournie par le détecteur de fin de tour, en epoch ms). */
    public synchronized void markSpeechEnd(long epochMs) {
        if (active && speechEnd == 0) speechEnd = epochMs;
    }

    /** Transcript STT disponible. */
    public synchronized void markSttDone() {
        if (active && sttDone == 0) sttDone = System.currentTimeMillis();
    }

    /** Prompt conversationnel construit (RAG inclus). */
    public synchronized void markPromptBuilt() {
        if (active && promptBuilt == 0) promptBuilt = System.currentTimeMillis();
    }

    /** Premier chunk reçu du stream LLM (appelable à chaque chunk : seule la première marque compte). */
    public synchronized void markFirstToken() {
        if (active && firstToken == 0) firstToken = System.currentTimeMillis();
    }

    /** Début de lecture du premier chunk audio — clôture et logue le tour. */
    public synchronized void markFirstAudible() {
        if (!active || firstAudible != 0) return;
        firstAudible = System.currentTimeMillis();
        active = false;
        log.info("VOICE_TIMELINE eou_ms={} prompt_ms={} ttft_ms={} tts_ms={} total_ms={}",
                delta(speechEnd, sttDone),
                delta(sttDone, promptBuilt),
                delta(promptBuilt, firstToken),
                delta(firstToken, firstAudible),
                delta(speechEnd, firstAudible));
    }

    private String partial() {
        return "eou_ms=" + delta(speechEnd, sttDone)
                + " prompt_ms=" + delta(sttDone, promptBuilt)
                + " ttft_ms=" + delta(promptBuilt, firstToken);
    }

    private static long delta(long from, long to) {
        return (from > 0 && to > 0) ? to - from : -1;
    }
}
