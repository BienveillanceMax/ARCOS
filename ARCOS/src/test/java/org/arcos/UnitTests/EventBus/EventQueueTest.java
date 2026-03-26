package org.arcos.UnitTests.EventBus;

import org.arcos.EventBus.EventQueue;
import org.arcos.EventBus.Events.Event;
import org.arcos.EventBus.Events.EventPriority;
import org.arcos.EventBus.Events.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EventQueueTest {

    private EventQueue queue;

    @BeforeEach
    void setUp() {
        queue = new EventQueue();
    }

    @Test
    void offer_withDifferentPriorities_take_shouldReturnHighFirst() throws InterruptedException {
        // given
        Event<String> low = new Event<>(EventType.NOTIFICATION, EventPriority.LOW, "low", "test");
        Event<String> medium = new Event<>(EventType.NOTIFICATION, EventPriority.MEDIUM, "med", "test");
        Event<String> high = new Event<>(EventType.ALERT, EventPriority.HIGH, "high", "test");

        // when — insert in reverse priority order
        queue.offer(low);
        queue.offer(medium);
        queue.offer(high);

        // then — should come out HIGH, MEDIUM, LOW
        assertEquals(EventPriority.HIGH, queue.take().getPriority());
        assertEquals(EventPriority.MEDIUM, queue.take().getPriority());
        assertEquals(EventPriority.LOW, queue.take().getPriority());
    }

    @Test
    void offer_shouldIncrementCount_take_shouldDecrementCount() throws InterruptedException {
        // given
        Event<String> e1 = new Event<>(EventType.NOTIFICATION, "e1", "test");
        Event<String> e2 = new Event<>(EventType.NOTIFICATION, "e2", "test");
        Event<String> e3 = new Event<>(EventType.NOTIFICATION, "e3", "test");

        // when
        queue.offer(e1);
        queue.offer(e2);
        queue.offer(e3);

        // then
        assertEquals(3, queue.size());

        // when
        queue.take();

        // then
        assertEquals(2, queue.size());
    }

    @Test
    void offer_whenQueueFull_shouldReturnFalse() {
        // given
        EventQueue smallQueue = new EventQueue(2);
        Event<String> e1 = new Event<>(EventType.NOTIFICATION, "e1", "test");
        Event<String> e2 = new Event<>(EventType.NOTIFICATION, "e2", "test");
        Event<String> e3 = new Event<>(EventType.NOTIFICATION, "e3", "test");

        // when
        assertTrue(smallQueue.offer(e1));
        assertTrue(smallQueue.offer(e2));
        boolean thirdAdded = smallQueue.offer(e3);

        // then
        assertFalse(thirdAdded, "offer should return false when queue is full");
        assertEquals(2, smallQueue.size());
    }

    @Test
    void poll_onEmptyQueue_shouldReturnNull() throws InterruptedException {
        // given — empty queue

        // when
        Event<?> result = queue.poll(50);

        // then
        assertNull(result, "poll on empty queue should return null after timeout");
        assertEquals(0, queue.size());
    }

    @Test
    void clear_shouldEmptyQueueAndResetCount() {
        // given
        queue.offer(new Event<>(EventType.NOTIFICATION, "e1", "test"));
        queue.offer(new Event<>(EventType.ALERT, EventPriority.HIGH, "e2", "test"));
        assertEquals(2, queue.size());

        // when
        queue.clear();

        // then
        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());
    }

    @Test
    void peek_shouldReturnHighestPriorityWithoutRemoving() {
        // given
        queue.offer(new Event<>(EventType.NOTIFICATION, EventPriority.LOW, "low", "test"));
        queue.offer(new Event<>(EventType.ALERT, EventPriority.HIGH, "high", "test"));

        // when
        Event<?> peeked = queue.peek();

        // then
        assertNotNull(peeked);
        assertEquals(EventPriority.HIGH, peeked.getPriority());
        assertEquals(2, queue.size(), "peek should not remove the event");
    }

    @Test
    void poll_shouldDecrementCount() throws InterruptedException {
        // given
        queue.offer(new Event<>(EventType.NOTIFICATION, "e1", "test"));
        queue.offer(new Event<>(EventType.NOTIFICATION, "e2", "test"));

        // when
        Event<?> polled = queue.poll(100);

        // then
        assertNotNull(polled);
        assertEquals(1, queue.size());
    }

    @Test
    void getLoadPercentage_shouldReflectCurrentLoad() {
        // given
        EventQueue smallQueue = new EventQueue(100);
        for (int i = 0; i < 50; i++) {
            smallQueue.offer(new Event<>(EventType.NOTIFICATION, "e" + i, "test"));
        }

        // when
        double loadPct = smallQueue.getLoadPercentage();

        // then
        assertEquals(50.0, loadPct, 0.01);
    }

    @Test
    void defaultConstructor_shouldHaveCapacity10000() {
        // given/when
        EventQueue defaultQueue = new EventQueue();

        // then
        assertEquals(10000, defaultQueue.getMaxCapacity());
    }
}
