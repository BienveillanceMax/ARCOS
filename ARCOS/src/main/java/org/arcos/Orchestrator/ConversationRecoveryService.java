package org.arcos.Orchestrator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Récupération conversationnelle en 3 niveaux quand la parole de l'utilisateur est
 * détectée mais incompréhensible (Bohus &amp; Rudnicky 2005) : reformulation légère,
 * demande explicite, puis sortie propre — jamais deux fois le même message, jamais
 * de boucle infinie de "je n'ai pas compris".
 */
@Slf4j
@Component
public class ConversationRecoveryService {

    /** Message + faut-il rouvrir une fenêtre d'écoute après l'avoir prononcé. */
    public record Recovery(String message, boolean reopenWindow) { }

    private static final List<String> ESCALATION = List.of(
            "Je n'ai pas bien entendu, tu peux répéter ?",
            "Désolé, je n'ai toujours pas compris. Reformule autrement ?");
    private static final String GIVE_UP =
            "Je n'arrive vraiment pas à te comprendre. On réessaiera plus tard.";

    private final AtomicInteger consecutiveFailures = new AtomicInteger();

    /** À appeler pour chaque énoncé incompréhensible consécutif. */
    public Recovery onUnintelligible() {
        int failures = consecutiveFailures.incrementAndGet();
        log.info("Parole incompréhensible ({} échec(s) consécutif(s))", failures);
        if (failures <= ESCALATION.size()) {
            return new Recovery(ESCALATION.get(failures - 1), true);
        }
        reset();
        return new Recovery(GIVE_UP, false);
    }

    /** À appeler dès qu'un énoncé est compris — l'escalade repart de zéro. */
    public void reset() {
        consecutiveFailures.set(0);
    }
}
