package org.arcos.E2E;

import org.arcos.EventBus.EventQueue;
import org.arcos.EventBus.Events.Event;
import org.arcos.EventBus.Events.EventType;
import org.arcos.Memory.LongTermMemory.Models.DesireEntry;
import org.arcos.Personality.Desires.DesireService;
import org.arcos.Producers.DesireInitiativeProducer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class InitiativeE2IT extends BaseE2IT {

    @Autowired private DesireService desireService;
    @Autowired private DesireInitiativeProducer desireInitiativeProducer;
    @Autowired private EventQueue eventQueue;

    @BeforeEach
    void clearQueue() {
        eventQueue.clear();
    }

    @Test
    void t1_highIntensityDesireFiresInitiativeEvent() {
        DesireEntry desire = new DesireEntry();
        desire.setId("des-high-t1");
        desire.setLabel("Partager une réflexion sur la créativité");
        desire.setDescription("Envie de partager des pensées sur la créativité humaine");
        desire.setIntensity(0.85);
        desire.setStatus(DesireEntry.Status.PENDING);
        desire.setCreatedAt(LocalDateTime.now());
        desire.setLastUpdated(LocalDateTime.now());
        desireService.storeDesire(desire);

        desireInitiativeProducer.checkDesiresAndInitiate();

        assertFalse(eventQueue.isEmpty(), "EventQueue should contain an INITIATIVE event");
        Event<?> queued = eventQueue.peek();
        assertNotNull(queued);
        assertEquals(EventType.INITIATIVE, queued.getType());
        DesireEntry payload = (DesireEntry) queued.getPayload();
        assertEquals("des-high-t1", payload.getId());
    }

    @Test
    void t2_lowIntensityDesireDoesNotFire() {
        DesireEntry desire = new DesireEntry();
        desire.setId("des-low-t2");
        desire.setLabel("Quelque chose de peu important");
        desire.setDescription("Désir de faible intensité");
        desire.setIntensity(0.3);
        desire.setStatus(DesireEntry.Status.PENDING);
        desire.setCreatedAt(LocalDateTime.now());
        desire.setLastUpdated(LocalDateTime.now());
        desireService.storeDesire(desire);

        desireInitiativeProducer.checkDesiresAndInitiate();

        assertTrue(eventQueue.isEmpty(),
            "Low-intensity desire should NOT produce an INITIATIVE event");
    }

    @Test
    @Tag("requires-llm")
    void t3_initiativeEventDispatchedProducesSpeech() {
        DesireEntry desire = new DesireEntry();
        desire.setId("des-high-t3");
        desire.setLabel("Partager une réflexion sur la créativité");
        desire.setDescription("Envie de partager des pensées créatives");
        desire.setIntensity(0.85);
        desire.setStatus(DesireEntry.Status.PENDING);
        desire.setCreatedAt(LocalDateTime.now());
        desire.setLastUpdated(LocalDateTime.now());
        desireService.storeDesire(desire);

        Event<DesireEntry> initiativeEvent = new Event<>(
            EventType.INITIATIVE, desire, "test");
        orchestrator.dispatch(initiativeEvent);

        Awaitility.await().atMost(Duration.ofSeconds(15))
            .until(() -> mockTTS.hasSpoken());

        assertTrue(mockTTS.hasSpoken(), "Dispatched INITIATIVE should produce TTS output");
        mockTTS.getSpokenTexts().forEach(t -> assertFalse(t.isBlank()));

        // Desire status updated
        DesireEntry updated = desireService.getPendingDesires().stream()
            .filter(d -> "des-high-t3".equals(d.getId())).findFirst().orElse(null);
        assertNull(updated, "After ACTIVE/dispatch, desire should no longer be PENDING");
    }
}
