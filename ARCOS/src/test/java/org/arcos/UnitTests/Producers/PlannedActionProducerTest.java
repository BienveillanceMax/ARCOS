package org.arcos.UnitTests.Producers;

import org.arcos.EventBus.EventQueue;
import org.arcos.PlannedAction.Models.PlannedActionEntry;
import org.arcos.Producers.PlannedActionProducer;
import org.arcos.common.utils.ObjectCreationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlannedActionProducerTest {

    @Mock
    private EventQueue eventQueue;

    private PlannedActionProducer producer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        producer = new PlannedActionProducer(eventQueue);
    }

    @Test
    void onActionTriggered_ShouldPushEventToQueue() {
        // Given
        PlannedActionEntry entry = ObjectCreationUtils.createSimpleReminderEntry();
        when(eventQueue.offer(any())).thenReturn(true);

        // When
        producer.onActionTriggered(entry);

        // Then
        verify(eventQueue).offer(any());
        assertNotNull(entry.getLastExecutedAt());
        assertEquals(1, entry.getExecutionCount());
    }

    @Test
    void onActionTriggered_QueueFull_ShouldNotThrow() {
        // Given
        PlannedActionEntry entry = ObjectCreationUtils.createSimpleReminderEntry();
        when(eventQueue.offer(any())).thenReturn(false);

        // When / Then
        assertDoesNotThrow(() -> producer.onActionTriggered(entry));
        verify(eventQueue).offer(any());
    }

    @Test
    void onActionTriggered_ShouldIncrementExecutionCount() {
        // Given
        PlannedActionEntry entry = ObjectCreationUtils.createSimpleReminderEntry();
        entry.setExecutionCount(5);
        when(eventQueue.offer(any())).thenReturn(true);

        // When
        producer.onActionTriggered(entry);

        // Then
        assertEquals(6, entry.getExecutionCount());
    }

    @Test
    void onReminderTriggered_ShouldPushEventWithReminderFlag() {
        // Given
        PlannedActionEntry entry = ObjectCreationUtils.createDeadlineWithRemindersEntry();
        when(eventQueue.offer(any())).thenReturn(true);

        // When
        producer.onReminderTriggered(entry);

        // Then
        verify(eventQueue).offer(any());
        assertTrue(entry.isReminderTrigger());
    }

    @Test
    void onReminderTriggered_QueueFull_ShouldNotThrow() {
        // Given
        PlannedActionEntry entry = ObjectCreationUtils.createDeadlineWithRemindersEntry();
        when(eventQueue.offer(any())).thenReturn(false);

        // When / Then
        assertDoesNotThrow(() -> producer.onReminderTriggered(entry));
        assertTrue(entry.isReminderTrigger());
    }
}
