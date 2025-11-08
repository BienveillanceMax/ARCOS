package Producers;

import EventBus.EventQueue;
import EventBus.Events.Event;
import EventBus.Events.EventPriority;
import EventBus.Events.EventType;
import Memory.LongTermMemory.Models.DesireEntry;
import Memory.LongTermMemory.Repositories.DesireRepository;
import Memory.LongTermMemory.service.MemoryService;
import Personality.Desires.DesireService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Component
@EnableScheduling
@Slf4j
public class DesireInitiativeProducer {

    private static final double INITIATIVE_THRESHOLD = 0.8;

    private final EventQueue eventQueue;
    private final DesireService desireService;

    @Autowired
    public DesireInitiativeProducer(EventQueue eventQueue, DesireService desireService) {
        this.eventQueue = eventQueue;
        this.desireService = desireService;
    }

    @Scheduled(fixedRate = 6000000) // Check every hour
    public void checkDesiresAndInitiate() {
        log.info("Checking for high-intensity desires...");
        List<DesireEntry> pendingDesires = desireService.getPendingDesires();
        log.info("High intensity desires found: {}", pendingDesires.size());
        for (DesireEntry desire : pendingDesires) {
            if (desire.getIntensity() >= INITIATIVE_THRESHOLD) {
                if (isGoodMomentToInitiate(desire)) {
                    log.info("High-intensity desire found, initiating... {}", desire.getLabel());
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

        // Push the event to the queue
        eventQueue.offer(initiativeEvent);

        // Update the desire's status to ACTIVE
        desire.setStatus(DesireEntry.Status.ACTIVE);
        desireService.storeDesire(desire);
    }
}

