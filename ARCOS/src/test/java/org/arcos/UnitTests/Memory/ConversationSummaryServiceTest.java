package org.arcos.UnitTests.Memory;

import org.arcos.LLM.Client.LLMClient;
import org.arcos.Memory.ConversationSummaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour ConversationSummaryService.
 * Couvre : summarizeAsync, prompt construction, gestion d'erreurs.
 */
class ConversationSummaryServiceTest {

    @Mock
    private LLMClient llmClient;

    private ConversationSummaryService service;
    private final Executor testExecutor = Executors.newSingleThreadExecutor();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ConversationSummaryService(llmClient);
    }

    @Test
    void summarizeAsync_ShouldReturnSummary_WhenLLMResponds() throws ExecutionException, InterruptedException {
        when(llmClient.generateToollessResponse(any(Prompt.class)))
                .thenReturn("L'utilisateur a parlé de films d'action et de météo.");

        String conversation = "USER: j'aime les films d'action\nASSISTANT: Ah oui, les films d'action.\n"
                + "USER: c'est quoi la météo\nASSISTANT: Il fait 12°C.";

        CompletableFuture<String> future = service.summarizeAsync(testExecutor,conversation);
        String summary = future.get();

        assertEquals("L'utilisateur a parlé de films d'action et de météo.", summary);
        verify(llmClient, times(1)).generateToollessResponse(any(Prompt.class));
    }

    @Test
    void summarizeAsync_ShouldReturnEmpty_WhenLLMThrowsException() throws ExecutionException, InterruptedException {
        when(llmClient.generateToollessResponse(any(Prompt.class)))
                .thenThrow(new RuntimeException("LLM indisponible"));

        CompletableFuture<String> future = service.summarizeAsync(testExecutor,"USER: bonjour\nASSISTANT: Salut!");
        String summary = future.get();

        assertEquals("", summary);
    }

    @Test
    void summarizeAsync_ShouldReturnEmpty_WhenLLMReturnsBlank() throws ExecutionException, InterruptedException {
        when(llmClient.generateToollessResponse(any(Prompt.class)))
                .thenReturn("   ");

        CompletableFuture<String> future = service.summarizeAsync(testExecutor,"USER: test\nASSISTANT: ok");
        String summary = future.get();

        assertEquals("", summary);
    }

    @Test
    void summarizeAsync_ShouldTrimWhitespace() throws ExecutionException, InterruptedException {
        when(llmClient.generateToollessResponse(any(Prompt.class)))
                .thenReturn("  Résumé avec espaces.  ");

        CompletableFuture<String> future = service.summarizeAsync(testExecutor,"USER: test\nASSISTANT: ok");
        String summary = future.get();

        assertEquals("Résumé avec espaces.", summary);
    }
}
