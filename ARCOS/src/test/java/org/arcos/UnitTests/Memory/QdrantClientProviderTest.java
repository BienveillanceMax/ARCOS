package org.arcos.UnitTests.Memory;

import org.arcos.Memory.LongTermMemory.Qdrant.QdrantClientProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class QdrantClientProviderTest {

    private static final String INVALID_HOST = "invalid-host";
    private static final int INVALID_PORT = 1;
    private static final int MAX_RETRIES_TEST = 2;
    private static final long INITIAL_BACKOFF_MS_TEST = 100;
    private static final long MAX_BACKOFF_MS_TEST = 200;

    @Test
    void constructor_WhenQdrantUnavailable_ShouldThrowRuntimeExceptionAfterMaxRetries() {
        // Given
        // Un host invalide et un port inaccessible simulent un Qdrant indisponible.
        // MAX_RETRIES_TEST=2 et backoff réduit pour que le test reste rapide.

        // When / Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                new QdrantClientProvider(INVALID_HOST, INVALID_PORT, MAX_RETRIES_TEST, INITIAL_BACKOFF_MS_TEST, MAX_BACKOFF_MS_TEST)
        );

        assertTrue(
                exception.getMessage().contains(INVALID_HOST),
                "Le message d'erreur doit contenir le host utilisé"
        );
        assertTrue(
                exception.getMessage().contains(String.valueOf(INVALID_PORT)),
                "Le message d'erreur doit contenir le port utilisé"
        );
        assertTrue(
                exception.getMessage().contains(String.valueOf(MAX_RETRIES_TEST)),
                "Le message d'erreur doit indiquer le nombre de tentatives effectuées"
        );
    }

    @Test
    void constructor_WhenInterrupted_ShouldPropagateInterruption() {
        // Given
        Thread testThread = new Thread(() -> {
            // When
            Thread.currentThread().interrupt();
            RuntimeException exception = assertThrows(RuntimeException.class, () ->
                    new QdrantClientProvider(INVALID_HOST, INVALID_PORT, MAX_RETRIES_TEST, INITIAL_BACKOFF_MS_TEST, MAX_BACKOFF_MS_TEST)
            );

            // Then
            assertTrue(
                    exception.getMessage().contains("Interrupted"),
                    "Une interruption de thread doit être propagée comme RuntimeException"
            );
        });

        testThread.start();
        try {
            testThread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Le thread de test a été interrompu de façon inattendue");
        }
        assertFalse(testThread.isAlive(), "Le thread de test doit s'être terminé");
    }

    @Test
    void defaultConstants_ShouldHaveExpectedValues() {
        // Given / When / Then
        assertEquals(6, QdrantClientProvider.DEFAULT_MAX_RETRIES,
                "Le nombre de tentatives par défaut doit être 6");
        assertEquals(1_000L, QdrantClientProvider.DEFAULT_INITIAL_BACKOFF_MS,
                "Le backoff initial par défaut doit être 1000ms");
        assertEquals(30_000L, QdrantClientProvider.DEFAULT_MAX_BACKOFF_MS,
                "Le backoff maximum par défaut doit être 30000ms");
    }
}
