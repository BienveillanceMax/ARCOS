package org.arcos.UnitTests.Producers;

import org.arcos.Configuration.AudioProperties;
import org.arcos.Configuration.SpeechToTextProperties;
import org.arcos.EventBus.EventQueue;
import org.arcos.IO.OuputHandling.StateHandler.AudioCue.AudioCueFeedbackHandler;
import org.arcos.IO.OuputHandling.StateHandler.CentralFeedBackHandler;
import org.arcos.Producers.WakeWordProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import lombok.extern.slf4j.Slf4j;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@ExtendWith(MockitoExtension.class)
class WakeWordProducerTest {

    @Mock
    private EventQueue eventQueue;

    @Mock
    private CentralFeedBackHandler centralFeedBackHandler;

    @Mock
    private AudioCueFeedbackHandler audioCueFeedbackHandler;

    private AudioProperties defaultAudioProperties() {
        return new AudioProperties();
    }

    @Test
    void startAfterStartup_WhenPorcupineResourcesAbsent_ShouldDisableAndNotStartThread() {
        // Given
        WakeWordProducer producer = buildProducerViaDegradedPath();

        // When
        assertThatCode(producer::startAfterStartup).doesNotThrowAnyException();

        // Then : porcupineEnabled doit être false et aucun thread démarré
        boolean enabled = (boolean) ReflectionTestUtils.getField(producer, "porcupineEnabled");
        assertThat(enabled).isFalse();
        Thread wakeWordThread = (Thread) ReflectionTestUtils.getField(producer, "wakeWordThread");
        assertThat(wakeWordThread).isNull();
    }

    @Test
    void run_WhenPorcupineDisabled_ShouldReturnImmediatelyWithoutException() {
        // Given
        WakeWordProducer producer = buildProducerViaDegradedPath();

        // When / Then : run() doit se terminer immédiatement sans exception
        assertThatCode(producer::run).doesNotThrowAnyException();
    }

    @Test
    void shutdown_WhenPorcupineDisabled_ShouldNotThrow() {
        // Given
        WakeWordProducer producer = buildProducerViaDegradedPath();

        // When / Then : shutdown() doit être sans effet et sans exception
        assertThatCode(producer::shutdown).doesNotThrowAnyException();
    }

    @Test
    void openConversationWindow_WhenPorcupineDisabled_ShouldNotSetWindowMode() {
        // Given
        WakeWordProducer producer = buildProducerViaDegradedPath();

        // When
        assertThatCode(() -> producer.openConversationWindow(3000)).doesNotThrowAnyException();

        // Then: inConversationWindowMode reste false (porcupineEnabled est false)
        boolean mode = (boolean) ReflectionTestUtils.getField(producer, "inConversationWindowMode");
        assertThat(mode).isFalse();
    }

    /**
     * Crée un WakeWordProducer sans déclencher l'init Porcupine.
     * Le constructeur ne fait que stocker les dépendances ;
     * l'init native est différée à startAfterStartup().
     */
    private WakeWordProducer buildProducerViaDegradedPath() {
        WakeWordProducer[] holder = new WakeWordProducer[1];
        assertThatCode(() -> {
            holder[0] = new WakeWordProducer(eventQueue, centralFeedBackHandler, audioCueFeedbackHandler, defaultAudioProperties(), new SpeechToTextProperties());
        }).doesNotThrowAnyException();
        return holder[0];
    }
}
