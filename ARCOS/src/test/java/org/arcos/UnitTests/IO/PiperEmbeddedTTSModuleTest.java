package org.arcos.UnitTests.IO;

import org.arcos.IO.OuputHandling.PiperEmbeddedTTSModule;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PiperEmbeddedTTSModuleTest {

    private PiperEmbeddedTTSModule ttsModule;

    @BeforeAll
    static void skipIfPiperEnabled() {
        // Ces tests vérifient le comportement quand Piper est ABSENT.
        // Si le binaire est présent et fonctionnel, on ignore cette suite.
        String piperDir = System.getProperty("user.home") + "/.piper-tts";
        File piperFile = new File(piperDir + "/piper/piper");
        Assumptions.assumeTrue(
            !piperFile.exists() || !piperFile.canExecute(),
            "Piper TTS installé — tests du mode désactivé ignorés"
        );
    }

    @BeforeEach
    void setUp() {
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
