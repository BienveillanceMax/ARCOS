package org.arcos.UnitTests.EventBus;

import org.arcos.EventBus.Events.Event;
import org.arcos.EventBus.Events.EventPriority;
import org.arcos.EventBus.Events.EventType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EventTest {

    @Test
    void compareTo_highShouldBeLessThanMedium_forMinHeapOrdering() {
        // given
        Event<String> high = new Event<>(EventType.ALERT, EventPriority.HIGH, "h", "test");
        Event<String> medium = new Event<>(EventType.NOTIFICATION, EventPriority.MEDIUM, "m", "test");

        // when/then — HIGH < MEDIUM so PriorityBlockingQueue dequeues HIGH first
        assertTrue(high.compareTo(medium) < 0);
    }

    @Test
    void compareTo_mediumShouldBeLessThanLow() {
        // given
        Event<String> medium = new Event<>(EventType.NOTIFICATION, EventPriority.MEDIUM, "m", "test");
        Event<String> low = new Event<>(EventType.NOTIFICATION, EventPriority.LOW, "l", "test");

        // when/then
        assertTrue(medium.compareTo(low) < 0);
    }

    @Test
    void compareTo_samePriority_shouldReturnZero() {
        // given
        Event<String> e1 = new Event<>(EventType.ALERT, EventPriority.HIGH, "a", "test");
        Event<String> e2 = new Event<>(EventType.NOTIFICATION, EventPriority.HIGH, "b", "test");

        // when/then
        assertEquals(0, e1.compareTo(e2));
    }

    @Test
    void constructorWithoutPriority_shouldDefaultToMedium() {
        // given/when
        Event<String> event = new Event<>(EventType.NOTIFICATION, "payload", "test");

        // then
        assertEquals(EventPriority.MEDIUM, event.getPriority());
    }

    @Test
    void constructor_shouldGenerateNonNullIdAndTimestamp() {
        // given/when
        Event<String> event = new Event<>(EventType.NOTIFICATION, "payload", "test");

        // then
        assertNotNull(event.getId(), "Event id should not be null");
        assertFalse(event.getId().isBlank(), "Event id should not be blank");
        assertNotNull(event.getTimestamp(), "Event timestamp should not be null");
    }

    @Test
    void constructor_shouldPreserveAllFields() {
        // given/when
        Event<String> event = new Event<>(EventType.ALERT, EventPriority.HIGH, "myPayload", "mySource");

        // then
        assertEquals(EventType.ALERT, event.getType());
        assertEquals(EventPriority.HIGH, event.getPriority());
        assertEquals("myPayload", event.getPayload());
        assertEquals("mySource", event.getSource());
    }
}
