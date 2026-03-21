package org.arcos.UnitTests.Producers;

import org.arcos.Configuration.PersonalityProperties;
import org.arcos.EventBus.EventQueue;
import org.arcos.EventBus.Events.Event;
import org.arcos.EventBus.Events.EventPriority;
import org.arcos.EventBus.Events.EventType;
import org.arcos.IO.OuputHandling.StateHandler.CentralFeedBackHandler;
import org.arcos.IO.OuputHandling.StateHandler.FeedBackEvent;
import org.arcos.Memory.LongTermMemory.Models.DesireEntry;
import org.arcos.Personality.Desires.DesireService;
import org.arcos.Personality.Mood.MoodService;
import org.arcos.Producers.DesireInitiativeProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DesireInitiativeProducerTest {

    @Mock
    private EventQueue eventQueue;

    @Mock
    private DesireService desireService;

    @Mock
    private CentralFeedBackHandler centralFeedBackHandler;

    @Mock
    private MoodService moodService;

    private PersonalityProperties personalityProperties;
    private DesireInitiativeProducer producer;

    @BeforeEach
    void setUp() {
        personalityProperties = new PersonalityProperties();
        personalityProperties.setInitiativeThreshold(0.8);
        personalityProperties.setInitiativeNoInitiativeUntilHour(9);
        producer = new DesireInitiativeProducer(eventQueue, desireService,
                centralFeedBackHandler, personalityProperties, moodService);
    }

    @Test
    void checkDesiresAndInitiate_WhenNoPendingDesires_ShouldNotQueueAnyEvent() {
        // Given
        when(desireService.getPendingDesires()).thenReturn(Collections.emptyList());
        when(moodService.getEffectiveInitiativeThreshold(anyDouble())).thenReturn(0.8);

        // When
        producer.checkDesiresAndInitiate();

        // Then
        verify(eventQueue, never()).offer(any());
    }

    @Test
    void checkDesiresAndInitiate_WhenDesireBelowThreshold_ShouldNotQueueEvent() {
        // Given
        DesireEntry lowDesire = createDesire(0.5, DesireEntry.Status.PENDING);
        when(desireService.getPendingDesires()).thenReturn(List.of(lowDesire));
        when(moodService.getEffectiveInitiativeThreshold(0.8)).thenReturn(0.8);

        // When
        producer.checkDesiresAndInitiate();

        // Then
        verify(eventQueue, never()).offer(any());
    }

    @Test
    void checkDesiresAndInitiate_WhenDesireAboveThreshold_ShouldQueueInitiativeEvent() {
        // Given
        DesireEntry highDesire = createDesire(0.9, DesireEntry.Status.PENDING);
        when(desireService.getPendingDesires()).thenReturn(List.of(highDesire));
        when(moodService.getEffectiveInitiativeThreshold(0.8)).thenReturn(0.8);
        when(eventQueue.offer(any())).thenReturn(true);

        // When
        producer.checkDesiresAndInitiate();

        // Then
        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(eventQueue).offer(captor.capture());

        Event<?> event = captor.getValue();
        assertThat(event.getType()).isEqualTo(EventType.INITIATIVE);
        assertThat(event.getPriority()).isEqualTo(EventPriority.LOW);
        assertThat(event.getPayload()).isEqualTo(highDesire);
        assertThat(event.getSource()).isEqualTo("DesireInitiativeProducer");
    }

    @Test
    void checkDesiresAndInitiate_WhenDesireAboveThreshold_ShouldMarkAsActive() {
        // Given
        DesireEntry highDesire = createDesire(0.9, DesireEntry.Status.PENDING);
        when(desireService.getPendingDesires()).thenReturn(List.of(highDesire));
        when(moodService.getEffectiveInitiativeThreshold(0.8)).thenReturn(0.8);
        when(eventQueue.offer(any())).thenReturn(true);

        // When
        producer.checkDesiresAndInitiate();

        // Then
        assertThat(highDesire.getStatus()).isEqualTo(DesireEntry.Status.ACTIVE);
        verify(desireService).storeDesire(highDesire);
    }

    @Test
    void checkDesiresAndInitiate_WhenQueueFull_ShouldNotMarkDesireAsActive() {
        // Given
        DesireEntry highDesire = createDesire(0.9, DesireEntry.Status.PENDING);
        when(desireService.getPendingDesires()).thenReturn(List.of(highDesire));
        when(moodService.getEffectiveInitiativeThreshold(0.8)).thenReturn(0.8);
        when(eventQueue.offer(any())).thenReturn(false);

        // When
        producer.checkDesiresAndInitiate();

        // Then
        assertThat(highDesire.getStatus()).isEqualTo(DesireEntry.Status.PENDING);
        verify(desireService, never()).storeDesire(any());
    }

    @Test
    void checkDesiresAndInitiate_WhenDesireAboveThreshold_ShouldEmitFeedback() {
        // Given
        DesireEntry highDesire = createDesire(0.9, DesireEntry.Status.PENDING);
        when(desireService.getPendingDesires()).thenReturn(List.of(highDesire));
        when(moodService.getEffectiveInitiativeThreshold(0.8)).thenReturn(0.8);
        when(eventQueue.offer(any())).thenReturn(true);

        // When
        producer.checkDesiresAndInitiate();

        // Then
        verify(centralFeedBackHandler).handleFeedBack(any(FeedBackEvent.class));
    }

    @Test
    void checkDesiresAndInitiate_WithMoodAdjustedThreshold_ShouldUseAdjustedValue() {
        // Given: mood lowers the threshold to 0.7
        DesireEntry desire = createDesire(0.75, DesireEntry.Status.PENDING);
        when(desireService.getPendingDesires()).thenReturn(List.of(desire));
        when(moodService.getEffectiveInitiativeThreshold(0.8)).thenReturn(0.7);
        when(eventQueue.offer(any())).thenReturn(true);

        // When
        producer.checkDesiresAndInitiate();

        // Then: desire at 0.75 >= adjusted threshold 0.7, so it should be queued
        verify(eventQueue).offer(any());
    }

    @Test
    void checkDesiresAndInitiate_WithMultipleDesires_ShouldProcessEachIndependently() {
        // Given
        DesireEntry highDesire = createDesire(0.9, DesireEntry.Status.PENDING);
        DesireEntry lowDesire = createDesire(0.5, DesireEntry.Status.PENDING);
        when(desireService.getPendingDesires()).thenReturn(List.of(highDesire, lowDesire));
        when(moodService.getEffectiveInitiativeThreshold(0.8)).thenReturn(0.8);
        when(eventQueue.offer(any())).thenReturn(true);

        // When
        producer.checkDesiresAndInitiate();

        // Then: only the high desire should be queued
        verify(eventQueue, times(1)).offer(any());
        assertThat(highDesire.getStatus()).isEqualTo(DesireEntry.Status.ACTIVE);
        assertThat(lowDesire.getStatus()).isEqualTo(DesireEntry.Status.PENDING);
    }

    private DesireEntry createDesire(double intensity, DesireEntry.Status status) {
        DesireEntry entry = new DesireEntry();
        entry.setId("test-desire-" + intensity);
        entry.setLabel("Test desire");
        entry.setDescription("A test desire");
        entry.setIntensity(intensity);
        entry.setStatus(status);
        return entry;
    }
}
