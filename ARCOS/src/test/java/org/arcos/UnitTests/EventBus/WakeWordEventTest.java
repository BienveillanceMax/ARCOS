package org.arcos.UnitTests.EventBus;

import org.arcos.EventBus.EventQueue;
import org.arcos.EventBus.Events.EventPriority;
import org.arcos.EventBus.Events.EventType;
import org.arcos.EventBus.Events.WakeWordEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WakeWordEventTest {

    @Test
    void defaultConstructor_shouldSetMultiTurnFalse() {
        // given/when
        WakeWordEvent event = new WakeWordEvent("wake", "porcupine");

        // then
        assertFalse(event.isMultiTurn(), "Default multiTurn should be false");
    }

    @Test
    void constructorWithMultiTurnTrue_shouldPreserveFlag() {
        // given/when
        WakeWordEvent event = new WakeWordEvent("wake", "porcupine", true);

        // then
        assertTrue(event.isMultiTurn());
    }

    @Test
    void multiTurnFlag_shouldBePreservedAfterOfferAndTake() throws InterruptedException {
        // given
        EventQueue queue = new EventQueue();
        WakeWordEvent original = new WakeWordEvent("wake", "porcupine", true);

        // when
        queue.offer(original);
        WakeWordEvent retrieved = (WakeWordEvent) queue.take();

        // then
        assertTrue(retrieved.isMultiTurn(), "multiTurn flag should be preserved through queue");
    }

    @Test
    void wakeWordEvent_shouldAlwaysHaveHighPriority() {
        // given/when
        WakeWordEvent event = new WakeWordEvent("wake", "porcupine");

        // then
        assertEquals(EventPriority.HIGH, event.getPriority());
    }

    @Test
    void wakeWordEvent_shouldAlwaysHaveWakewordType() {
        // given/when
        WakeWordEvent event = new WakeWordEvent("wake", "porcupine");

        // then
        assertEquals(EventType.WAKEWORD, event.getType());
    }
}
