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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour ConversationSummaryService.
 * Couvre : summarizeAsync, prompt construction, gestion d'erreurs.
 */
class ConversationSummaryServiceTest {

    @Mock
    private LLMClient llmClient;

    private ConversationSummaryService service;
    private final ExecutorService testExecutor = Executors.newSingleThreadExecutor();

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        testExecutor.shutdownNow();
    }

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

    // ── Gaps comblés — Sprint 8, Story 1.11 ─────────────────────────────────

    @Test
    void summarizeAsync_ShouldReturnEmpty_WhenLLMReturnsNull() throws ExecutionException, InterruptedException {
        when(llmClient.generateToollessResponse(any(Prompt.class)))
                .thenReturn(null);

        CompletableFuture<String> future = service.summarizeAsync(testExecutor, "USER: test\nASSISTANT: ok");
        String summary = future.get();

        assertEquals("", summary);
    }

    @Test
    void summarizeAsync_ShouldPassConversationToLLM() throws ExecutionException, InterruptedException {
        // given — verify the prompt built from conversation reaches the LLM
        String conversation = "USER: J'aime la randonnée\nASSISTANT: Super !";
        when(llmClient.generateToollessResponse(argThat(prompt ->
                prompt.getContents().contains("randonnée"))))
                .thenReturn("Résumé de randonnée.");

        // when
        CompletableFuture<String> future = service.summarizeAsync(testExecutor, conversation);
        String summary = future.get();

        // then
        assertEquals("Résumé de randonnée.", summary);
        verify(llmClient).generateToollessResponse(argThat(prompt ->
                prompt.getContents().contains(conversation)));
    }
}
