package org.arcos.UnitTests.IO;

import org.arcos.IO.OuputHandling.PiperEmbeddedTTSModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PiperEmbeddedTTSModuleTest {

    private PiperEmbeddedTTSModule ttsModule;

    @BeforeEach
    void setUp() {
        // Le constructeur échoue gracieusement dans l'environnement de test (Piper absent)
        ttsModule = new PiperEmbeddedTTSModule();
    }

    @Test
    void isEnabled_shouldReturnFalse_whenPiperNotInstalled() {
        // Given : l'environnement de test ne dispose pas de Piper
        // When / Then
        assertFalse(ttsModule.isEnabled(),
                "isEnabled() doit retourner false quand Piper n'est pas installé");
    }

    @Test
    void speakAsync_shouldReturnNonNullFuture_whenDisabled() {
        // Given
        assertFalse(ttsModule.isEnabled());

        // When
        Future<Void> future = ttsModule.speakAsync("Bonjour le monde");

        // Then
        assertNotNull(future, "speakAsync() ne doit pas retourner null même quand TTS est désactivé");
    }

    @Test
    void speakAsync_withAllParameters_shouldReturnNonNullFuture_whenDisabled() {
        // Given
        assertFalse(ttsModule.isEnabled());

        // When
        Future<Void> future = ttsModule.speakAsync("Bonjour", 1.0f, 0.667f, 0.8f);

        // Then
        assertNotNull(future, "speakAsync() avec paramètres ne doit pas retourner null quand TTS est désactivé");
    }

    @Test
    void speak_shouldNotThrowException_whenDisabled() {
        // Given
        assertFalse(ttsModule.isEnabled());

        // When / Then : aucune exception ne doit être levée
        assertDoesNotThrow(() -> ttsModule.speak("Bonjour le monde"),
                "speak() ne doit pas lever d'exception quand TTS est désactivé");
    }

    @Test
    void speak_withAllParameters_shouldNotThrowException_whenDisabled() {
        // Given
        assertFalse(ttsModule.isEnabled());

        // When / Then
        assertDoesNotThrow(() -> ttsModule.speak("Bonjour", 1.0f, 0.667f, 0.8f),
                "speak() avec paramètres ne doit pas lever d'exception quand TTS est désactivé");
    }

    @Test
    void shutdown_shouldNotThrow_whenDisabled() {
        // Given
        assertFalse(ttsModule.isEnabled());

        // When / Then
        assertDoesNotThrow(() -> ttsModule.shutdown(),
                "shutdown() ne doit pas lever d'exception même quand TTS est désactivé");
    }
}
