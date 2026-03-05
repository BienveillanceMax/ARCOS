package org.arcos.UnitTests.Memory;

import org.arcos.LLM.Client.LLMClient;
import org.arcos.Memory.ConversationSummaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour ConversationSummaryService (STORY-RCS-002).
 * Couvre : initialisation, updateAsync, reset, exceptions, thread-safety.
 */
class ConversationSummaryServiceTest {

    @Mock
    private LLMClient llmClient;

    private ConversationSummaryService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ConversationSummaryService(llmClient);
    }

    // ===== T1 : état initial =====

    @Test
    void getSummary_ShouldReturnEmpty_WhenJustInitialized() {
        assertEquals("", service.getSummary());
    }

    // ===== T2 : updateAsync met à jour le résumé =====

    @Test
    void updateAsync_ShouldUpdateSummary_WhenLLMReturnsNewSummary() throws InterruptedException {
        when(llmClient.generateToollessResponse(any(Prompt.class)))
                .thenReturn("L'utilisateur aime les films d'action.");

        service.updateAsync("", "j'aime les films d'action", "Ah oui, les films d'action c'est divertissant.");

        // Attend la complétion du future async
        Thread.sleep(500);
        assertEquals("L'utilisateur aime les films d'action.", service.getSummary());
    }

    // ===== T3 : exception silencieuse — résumé précédent conservé =====

    @Test
    void updateAsync_ShouldKeepPreviousSummary_WhenLLMThrowsException() throws InterruptedException {
        // Pré-état : un résumé existant (1er appel OK)
        when(llmClient.generateToollessResponse(any(Prompt.class))).thenReturn("Résumé initial.");
        service.updateAsync("", "premier message", "première réponse");
        Thread.sleep(500);
        assertEquals("Résumé initial.", service.getSummary());

        // Le LLM lève une exception sur le 2e appel
        doThrow(new RuntimeException("LLM indisponible"))
                .when(llmClient).generateToollessResponse(any(Prompt.class));
        service.updateAsync("Résumé initial.", "deuxième message", "deuxième réponse");
        Thread.sleep(500);

        // Le résumé précédent doit être conservé, pas d'exception propagée
        assertEquals("Résumé initial.", service.getSummary());
    }

    // ===== T4 : reset efface le résumé =====

    @Test
    void reset_ShouldClearSummary_WhenSummaryExists() throws InterruptedException {
        when(llmClient.generateToollessResponse(any(Prompt.class))).thenReturn("Résumé de test.");
        service.updateAsync("", "bonjour", "Bonjour, que puis-je faire ?");
        Thread.sleep(500);
        assertEquals("Résumé de test.", service.getSummary());

        service.reset();

        assertEquals("", service.getSummary());
    }

    // ===== T5 : thread-safety — appels concurrents =====

    @Test
    void concurrentUpdateAndGet_ShouldNotThrow_AndReturnCoherentValue() throws InterruptedException {
        when(llmClient.generateToollessResponse(any(Prompt.class))).thenReturn("Résumé concurrent.");

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            int idx = i;
            new Thread(() -> {
                try {
                    startLatch.await();
                    if (idx % 2 == 0) {
                        service.updateAsync(service.getSummary(), "msg" + idx, "rép" + idx);
                    } else {
                        String val = service.getSummary();
                        assertNotNull(val, "getSummary ne doit jamais retourner null");
                    }
                } catch (Exception e) {
                    errors.add(e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        assertTrue(errors.isEmpty(), "Aucune erreur de concurrence attendue : " + errors);
    }

    // ===== T6 : résumé précédent vide (1er tour) =====

    @Test
    void updateAsync_ShouldGenerateSummary_WhenPreviousSummaryIsEmpty() throws InterruptedException {
        when(llmClient.generateToollessResponse(any(Prompt.class)))
                .thenReturn("L'utilisateur a demandé la météo à Lyon.");

        service.updateAsync("", "c'est quoi la météo", "Il fait 12°C à Lyon.");

        Thread.sleep(500);
        assertEquals("L'utilisateur a demandé la météo à Lyon.", service.getSummary());
        verify(llmClient, times(1)).generateToollessResponse(any(Prompt.class));
    }
}
