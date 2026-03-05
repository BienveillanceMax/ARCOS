package org.arcos.Memory;

import lombok.extern.slf4j.Slf4j;
import org.arcos.LLM.Client.LLMClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Maintient un résumé roulant de la session conversationnelle en cours.
 * Le résumé est mis à jour de façon asynchrone après chaque tour, sans bloquer la réponse.
 * Thread-safe via AtomicReference.
 */
@Slf4j
@Service
public class ConversationSummaryService {

    private final LLMClient llmClient;
    private final AtomicReference<String> currentSummary = new AtomicReference<>("");

    public ConversationSummaryService(LLMClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * Met à jour le résumé de façon non-bloquante (retourne immédiatement).
     * En cas d'erreur, le résumé précédent reste valide.
     */
    public void updateAsync(String previousSummary,
                            String lastUserMessage,
                            String lastAssistantMessage) {
        CompletableFuture.runAsync(() -> {
            try {
                Prompt summaryPrompt = buildSummaryUpdatePrompt(
                        previousSummary, lastUserMessage, lastAssistantMessage);
                String newSummary = llmClient.generateToollessResponse(summaryPrompt);
                if (newSummary != null && !newSummary.isBlank()) {
                    currentSummary.set(newSummary.trim());
                }
            } catch (Exception e) {
                log.warn("Résumé non mis à jour : {}", e.getMessage());
                // Silencieux — le résumé précédent reste valide
            }
        });
    }

    public String getSummary() {
        return currentSummary.get();
    }

    public void reset() {
        currentSummary.set("");
    }

    Prompt buildSummaryUpdatePrompt(String previousSummary,
                                    String lastUserMessage,
                                    String lastAssistantMessage) {
        String prev = (previousSummary == null || previousSummary.isBlank())
                ? "(aucun)"
                : previousSummary;
        String text = "Résumé précédent : " + prev + "\n"
                + "Dernier échange :\n"
                + "UTILISATEUR : " + lastUserMessage + "\n"
                + "CALCIFER : " + lastAssistantMessage + "\n\n"
                + "En 1 à 2 phrases maximum, mets à jour le résumé pour inclure les nouveaux éléments importants. "
                + "Réponds uniquement avec le résumé mis à jour, sans introduction.";
        return new Prompt(new SystemMessage(text));
    }
}
