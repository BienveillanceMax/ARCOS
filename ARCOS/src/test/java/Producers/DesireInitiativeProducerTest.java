package Producers;

import EventBus.EventQueue;
import EventBus.Events.Event;
import EventBus.Events.EventType;
import Memory.LongTermMemory.Models.DesireEntry;
import Memory.LongTermMemory.service.MemoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class DesireInitiativeProducerTest {

    @Mock
    private EventQueue eventQueue;

    @Mock
    private MemoryService memoryService;

    @InjectMocks
    private DesireInitiativeProducer desireInitiativeProducer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCheckDesiresAndInitiate_HighIntensityDesire_ShouldTriggerInitiative() {
        // Arrange
        DesireEntry highIntensityDesire = new DesireEntry();
        highIntensityDesire.setId("desire-1");
        highIntensityDesire.setIntensity(0.9);
        highIntensityDesire.setStatus(DesireEntry.Status.PENDING);
        highIntensityDesire.setLabel("High intensity desire");

        when(memoryService.getPendingDesires()).thenReturn(Collections.singletonList(highIntensityDesire));

        // Act
        desireInitiativeProducer.checkDesiresAndInitiate();

        // Assert
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventQueue, times(1)).offer(eventCaptor.capture());
        Event capturedEvent = eventCaptor.getValue();
        assertEquals(EventType.INITIATIVE, capturedEvent.getType());
        assertEquals(highIntensityDesire, capturedEvent.getPayload());

        ArgumentCaptor<DesireEntry> desireCaptor = ArgumentCaptor.forClass(DesireEntry.class);
        verify(memoryService, times(1)).storeDesire(desireCaptor.capture());
        assertEquals(DesireEntry.Status.ACTIVE, desireCaptor.getValue().getStatus());
    }

    @Test
    void testCheckDesiresAndInitiate_LowIntensityDesire_ShouldNotTriggerInitiative() {
        // Arrange
        DesireEntry lowIntensityDesire = new DesireEntry();
        lowIntensityDesire.setId("desire-2");
        lowIntensityDesire.setIntensity(0.5);
        lowIntensityDesire.setStatus(DesireEntry.Status.PENDING);
        lowIntensityDesire.setLabel("Low intensity desire");

        when(memoryService.getPendingDesires()).thenReturn(Collections.singletonList(lowIntensityDesire));

        // Act
        desireInitiativeProducer.checkDesiresAndInitiate();

        // Assert
        verify(eventQueue, never()).offer(any(Event.class));
        verify(memoryService, never()).storeDesire(any(DesireEntry.class));
    }
}