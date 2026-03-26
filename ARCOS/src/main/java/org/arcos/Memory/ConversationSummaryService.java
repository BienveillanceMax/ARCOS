package org.arcos.Memory;

import lombok.extern.slf4j.Slf4j;
import org.arcos.LLM.Client.LLMClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Génère un résumé de conversation à la fin d'une session.
 * Appelé une seule fois par session (et seulement si la conversation est assez longue).
 */
@Slf4j
@Service
public class ConversationSummaryService {

    private final LLMClient llmClient;

    public ConversationSummaryService(LLMClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * Résume la conversation complète de façon asynchrone sur l'executor fourni.
     * En cas d'erreur, retourne une chaîne vide.
     */
    public CompletableFuture<String> summarizeAsync(Executor executor, String fullConversation) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Prompt prompt = buildSummaryPrompt(fullConversation);
                String summary = llmClient.generateToollessResponse(prompt);
                return (summary != null && !summary.isBlank()) ? summary.trim() : "";
            } catch (Exception e) {
                log.warn("Summary generation failed: {}", e.getMessage());
                return "";
            }
        }, executor);
    }

    Prompt buildSummaryPrompt(String fullConversation) {
        String text = fullConversation + "\n\n"
                + "Résume cette conversation en 1-3 phrases. Éléments importants uniquement. Résumé seul, sans introduction.";
        return new Prompt(new SystemMessage(text));
    }
}
