package org.arcos.UnitTests.Producers;

import org.arcos.EventBus.EventQueue;
import org.arcos.EventBus.Events.Event;
import org.arcos.EventBus.Events.EventType;
import org.arcos.Producers.InactivityProducer;
import org.arcos.UserModel.UserModelProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InactivityProducerTest {

    @Mock
    private EventQueue eventQueue;

    private InactivityProducer producer;

    @BeforeEach
    void setUp() {
        UserModelProperties properties = new UserModelProperties();
        properties.setSessionEndThresholdMinutes(3);
        properties.setIdleThresholdMinutes(15);
        producer = new InactivityProducer(eventQueue, properties);
        lenient().when(eventQueue.offer(any())).thenReturn(true);
    }

    @Test
    void recordInteraction_ShouldActivateSession() {
        // given
        producer.recordInteraction();

        // then
        boolean sessionActive = (boolean) ReflectionTestUtils.getField(producer, "sessionActive");
        boolean hasHadSession = (boolean) ReflectionTestUtils.getField(producer, "hasHadSession");
        assertTrue(sessionActive);
        assertTrue(hasHadSession);
    }

    @Test
    void checkInactivity_WhenSessionActiveAndPastThreshold_ShouldEmitSessionEnd() {
        // given — simulate active session with old interaction time
        producer.recordInteraction();
        ReflectionTestUtils.setField(producer, "lastInteractionTime",
                LocalDateTime.now().minusMinutes(5));

        // when
        producer.checkInactivity();

        // then
        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(eventQueue).offer(captor.capture());
        assertEquals(EventType.SESSION_END, captor.getValue().getType());
    }

    @Test
    void checkInactivity_WhenPastIdleThresholdAndHadSession_ShouldEmitIdleWindowOpen() {
        // given — session ended, past idle threshold
        producer.recordInteraction();
        ReflectionTestUtils.setField(producer, "lastInteractionTime",
                LocalDateTime.now().minusMinutes(20));
        ReflectionTestUtils.setField(producer, "sessionActive", false);

        // when
        producer.checkInactivity();

        // then
        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(eventQueue).offer(captor.capture());
        assertEquals(EventType.IDLE_WINDOW_OPEN, captor.getValue().getType());
    }

    @Test
    void checkInactivity_WhenNeverHadSession_ShouldNotEmitIdleWindowOpen() {
        // given — no recordInteraction ever called, past idle threshold
        ReflectionTestUtils.setField(producer, "lastInteractionTime",
                LocalDateTime.now().minusMinutes(20));

        // when
        producer.checkInactivity();

        // then
        verify(eventQueue, never()).offer(any());
    }

    @Test
    void checkInactivity_WhenNotActiveAndAlreadyEmitted_ShouldReturnEarly() {
        // given
        ReflectionTestUtils.setField(producer, "sessionActive", false);
        ReflectionTestUtils.setField(producer, "idleWindowEmitted", true);

        // when
        producer.checkInactivity();

        // then
        verify(eventQueue, never()).offer(any());
    }

    @Test
    void checkInactivity_WhenSessionActiveButBelowThreshold_ShouldNotEmit() {
        // given — active session, recent interaction
        producer.recordInteraction();

        // when
        producer.checkInactivity();

        // then
        verify(eventQueue, never()).offer(any());
    }

    @Test
    void isIdle_ShouldReturnTrueWhenPastThreshold() {
        // given
        ReflectionTestUtils.setField(producer, "lastInteractionTime",
                LocalDateTime.now().minusMinutes(20));

        // then
        assertTrue(producer.isIdle());
    }

    @Test
    void isIdle_ShouldReturnFalseWhenRecent() {
        // given — recent interaction
        producer.recordInteraction();

        // then
        assertFalse(producer.isIdle());
    }

    @Test
    void constructor_ShouldRejectInvalidThresholds() {
        // given — sessionEnd >= idle
        UserModelProperties badProps = new UserModelProperties();
        badProps.setSessionEndThresholdMinutes(15);
        badProps.setIdleThresholdMinutes(15);

        // then
        assertThrows(IllegalArgumentException.class,
                () -> new InactivityProducer(eventQueue, badProps));
    }

    @Test
    void recordInteraction_ShouldResetIdleWindowEmitted() {
        // given — idle window was emitted
        ReflectionTestUtils.setField(producer, "idleWindowEmitted", true);

        // when
        producer.recordInteraction();

        // then
        boolean idleWindowEmitted = (boolean) ReflectionTestUtils.getField(producer, "idleWindowEmitted");
        assertFalse(idleWindowEmitted);
    }
}
