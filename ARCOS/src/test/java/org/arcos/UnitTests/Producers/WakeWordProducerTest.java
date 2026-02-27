package org.arcos.UnitTests.Producers;

import org.arcos.EventBus.EventQueue;
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

    @Test
    void constructor_WhenPorcupineResourcesAbsent_ShouldNotThrowAndDisablePorcupine() {
        // Given / When : les ressources Porcupine ne sont pas disponibles en environnement de test
        WakeWordProducer producer = buildProducerViaDegradedPath();

        // Then : le constructeur ne doit pas propager d'exception et porcupineEnabled doit être false
        boolean enabled = (boolean) ReflectionTestUtils.getField(producer, "porcupineEnabled");
        assertThat(enabled).isFalse();
    }

    @Test
    void startAfterStartup_WhenPorcupineDisabled_ShouldNotStartThread() {
        // Given
        WakeWordProducer producer = buildProducerViaDegradedPath();

        // When : startAfterStartup ne doit pas lever d'exception et ne doit pas démarrer le thread
        assertThatCode(producer::startAfterStartup).doesNotThrowAnyException();

        // Then : aucun thread wakeword ne doit être démarré
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

    /**
     * Crée un WakeWordProducer en passant par le chemin dégradé :
     * le constructeur intercepte l'absence des ressources Porcupine et
     * positionne porcupineEnabled = false sans propager d'exception.
     */
    private WakeWordProducer buildProducerViaDegradedPath() {
        // Le constructeur catchera l'IllegalArgumentException levée par extractResource()
        // car les fichiers .ppn / .pv ne sont pas dans le classpath de test.
        // On s'assure uniquement que l'instanciation se termine sans crash.
        WakeWordProducer[] holder = new WakeWordProducer[1];
        assertThatCode(() -> {
            holder[0] = new WakeWordProducer(eventQueue, "http://localhost:9000", centralFeedBackHandler);
        }).doesNotThrowAnyException();
        return holder[0];
    }
}
