package org.arcos.IO.InputHandling;

import lombok.extern.slf4j.Slf4j;
import org.arcos.Configuration.AudioProperties;
import org.springframework.stereotype.Component;

/**
 * Politique de fin de tour contextuelle : la durée de silence qui clôt un énoncé dépend du
 * fait qu'ARCOS vient ou non de poser une question (Skantze 2021 ; Heldner &amp; Edlund 2010).
 *
 * <p>Après une question, l'utilisateur réfléchit et fait des pauses plus longues → on rallonge
 * le seuil de silence pour ne pas le couper. Sinon on garde la valeur de base. On ne raccourcit
 * JAMAIS : un faux positif ne fait qu'attendre un peu plus, il ne coupe pas l'utilisateur.
 *
 * <p>Le coût latence est quasi nul depuis le STT spéculatif (EOU ≈ max(silence, round-trip STT),
 * et le round-trip STT domine). La distinction fine question ouverte/fermée a été retirée :
 * le classifieur par mots-clés se trompait trop souvent pour justifier un seuil raccourci.
 */
@Slf4j
@Component
public class EndpointingPolicyService {

    /** Facteur appliqué au silence de base quand ARCOS vient de poser une question. */
    private static final double QUESTION_FACTOR = 1.3;

    private final AudioProperties audioProperties;

    public EndpointingPolicyService(AudioProperties audioProperties) {
        this.audioProperties = audioProperties;
    }

    /** Durée de silence à appliquer à la fenêtre de conversation qui suit {@code assistantResponse}. */
    public long conversationSilenceMs(String assistantResponse) {
        long base = audioProperties.getConversationSilenceMs();
        boolean question = endsWithQuestion(assistantResponse);
        long silence = question ? Math.round(base * QUESTION_FACTOR) : base;
        log.debug("Endpointing: question={} silence={}ms (base {}ms)", question, silence, base);
        return silence;
    }

    /** {@code true} si la dernière réplique d'ARCOS se termine par une question. */
    public boolean endsWithQuestion(String assistantResponse) {
        return assistantResponse != null && assistantResponse.strip().endsWith("?");
    }
}
