package org.arcos.Producers;

import org.arcos.EventBus.EventQueue;
import org.arcos.EventBus.Events.Event;
import org.arcos.EventBus.Events.EventPriority;
import org.arcos.EventBus.Events.EventType;
import org.arcos.IO.OuputHandling.StateHandler.CentralFeedBackHandler;
import org.arcos.IO.OuputHandling.StateHandler.FeedBackEvent;
import org.arcos.IO.OuputHandling.StateHandler.UXEventType;
import org.arcos.Memory.LongTermMemory.Models.DesireEntry;
import org.arcos.Personality.Desires.DesireService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.List;

@Component
@EnableScheduling
@Slf4j
public class DesireInitiativeProducer {

    private static final double INITIATIVE_THRESHOLD = 0.8;

    private final EventQueue eventQueue;
    private final DesireService desireService;
    private final CentralFeedBackHandler centralFeedBackHandler;


    @Autowired
    public DesireInitiativeProducer(EventQueue eventQueue, DesireService desireService, CentralFeedBackHandler centralFeedBackHandler) {
        this.eventQueue = eventQueue;
        this.desireService = desireService;
        this.centralFeedBackHandler = centralFeedBackHandler;
    }

    @Scheduled(fixedRate = 3600000) // Check every hour
    public void checkDesiresAndInitiate() {
        log.info("Checking for high-intensity desires...");
        List<DesireEntry> pendingDesires = desireService.getPendingDesires();
        log.info("High intensity desires found: {}", pendingDesires.size());
        for (DesireEntry desire : pendingDesires) {
            if (desire.getIntensity() >= INITIATIVE_THRESHOLD) {
                if (isGoodMomentToInitiate(desire)) {
                    log.info("High-intensity desire found, initiating... {}", desire.getLabel());
                    centralFeedBackHandler.handleFeedBack(new FeedBackEvent(UXEventType.INITIATIVE_START));
                    initiateDesireAction(desire);
                }
            }
        }
    }

    private boolean isGoodMomentToInitiate(DesireEntry desire) {
        // Placeholder for modular logic to determine the right moment.
        // This can be expanded to check for user presence, conversation state, etc.

        if (LocalTime.now().isAfter(LocalTime.MIDNIGHT) && LocalTime.now().isBefore(LocalTime.of(9,0))) {
            return false;
        }
        return true;
    }

    private void initiateDesireAction(DesireEntry desire) {
        // Create an event for the orchestrator
        Event<DesireEntry> initiativeEvent = new Event<>(
                EventType.INITIATIVE,
                EventPriority.LOW, // Initiatives can be low priority compared to direct user interaction
                desire,
                "DesireInitiativeProducer"
        );

        // Only mark ACTIVE if the event was actually queued
        boolean queued = eventQueue.offer(initiativeEvent);
        if (queued) {
            desire.setStatus(DesireEntry.Status.ACTIVE);
            desireService.storeDesire(desire);
        } else {
            log.warn("Event queue full, could not queue initiative for desire {}", desire.getId());
        }
    }
}

