package org.arcos.IO.InputHandling;

import lombok.extern.slf4j.Slf4j;
import org.arcos.Configuration.AudioProperties;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Politique de fin de tour contextuelle : la durée de silence qui clôt un énoncé dépend du
 * type de la dernière réplique d'ARCOS (Skantze 2021 ; Heldner &amp; Edlund 2010).
 *
 * Après une question fermée (oui/non), l'utilisateur répond vite et court → seuil réduit.
 * Après une question ouverte, il réfléchit et fait des pauses → seuil rallongé.
 * Le coût latence est quasi nul depuis le STT spéculatif (EOU ≈ max(silence, round-trip STT)).
 */
@Slf4j
@Component
public class EndpointingPolicyService {

    public enum ResponseContextHint { YES_NO_QUESTION, OPEN_QUESTION, STATEMENT }

    /** Mots interrogatifs français ouvrant une question à réponse libre. */
    private static final Set<String> OPEN_INTERROGATIVES = Set.of(
            "quoi", "comment", "pourquoi", "où", "quand", "combien",
            "quel", "quelle", "quels", "quelles", "lequel", "laquelle", "lesquels", "lesquelles");
    private static final Pattern WORD_SPLIT = Pattern.compile("[\\s,;:'’\\-]+");

    private final AudioProperties audioProperties;

    public EndpointingPolicyService(AudioProperties audioProperties) {
        this.audioProperties = audioProperties;
    }

    /** Durée de silence à appliquer à la fenêtre de conversation qui suit {@code assistantResponse}. */
    public long conversationSilenceMs(String assistantResponse) {
        long base = audioProperties.getConversationSilenceMs();
        ResponseContextHint hint = classify(assistantResponse);
        long silence = switch (hint) {
            case YES_NO_QUESTION -> Math.round(base * 0.7); // réponse courte attendue
            case OPEN_QUESTION -> Math.round(base * 1.3);   // l'utilisateur réfléchit, pauses plus longues
            case STATEMENT -> base;
        };
        log.debug("Endpointing: hint={} silence={}ms (base {}ms)", hint, silence, base);
        return silence;
    }

    /**
     * Classe la dernière phrase de la réponse : question ouverte (mot interrogatif),
     * question fermée (finit par "?" sans mot interrogatif — inversion, "est-ce que",
     * intonation), ou affirmation.
     */
    public ResponseContextHint classify(String assistantResponse) {
        if (assistantResponse == null || assistantResponse.isBlank()) {
            return ResponseContextHint.STATEMENT;
        }
        String trimmed = assistantResponse.strip();
        if (!trimmed.endsWith("?")) {
            return ResponseContextHint.STATEMENT;
        }
        String lastSentence = lastSentenceOf(trimmed).toLowerCase(Locale.FRENCH);
        if (lastSentence.contains("qu'est-ce") || lastSentence.contains("qu’est-ce")) {
            return ResponseContextHint.OPEN_QUESTION;
        }
        for (String word : WORD_SPLIT.split(lastSentence)) {
            if (OPEN_INTERROGATIVES.contains(word)) {
                return ResponseContextHint.OPEN_QUESTION;
            }
        }
        return ResponseContextHint.YES_NO_QUESTION;
    }

    private static String lastSentenceOf(String text) {
        // Dernier segment après le terminateur de phrase précédent (le texte finit par "?")
        int boundary = Math.max(text.lastIndexOf('.', text.length() - 2),
                Math.max(text.lastIndexOf('!', text.length() - 2),
                        text.lastIndexOf('?', text.length() - 2)));
        return boundary >= 0 ? text.substring(boundary + 1) : text;
    }
}
