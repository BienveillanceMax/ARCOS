package org.arcos.Producers;

import org.arcos.Configuration.PersonalityProperties;
import org.arcos.EventBus.EventQueue;
import org.arcos.Personality.Mood.MoodService;
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


    private final EventQueue eventQueue;
    private final DesireService desireService;
    private final CentralFeedBackHandler centralFeedBackHandler;
    private final PersonalityProperties personalityProperties;
    private final MoodService moodService;

    @Autowired
    public DesireInitiativeProducer(EventQueue eventQueue, DesireService desireService,
                                    CentralFeedBackHandler centralFeedBackHandler,
                                    PersonalityProperties personalityProperties,
                                    MoodService moodService) {
        this.eventQueue = eventQueue;
        this.desireService = desireService;
        this.centralFeedBackHandler = centralFeedBackHandler;
        this.personalityProperties = personalityProperties;
        this.moodService = moodService;
    }

    @Scheduled(fixedRateString = "${arcos.personality.initiative-check-interval-ms:3600000}")
    public void checkDesiresAndInitiate() {
        List<DesireEntry> pendingDesires = desireService.getPendingDesires();
        double baseThreshold = personalityProperties.getInitiativeThreshold();
        double adjustedThreshold = moodService.getEffectiveInitiativeThreshold(baseThreshold);

        int triggered = 0;
        int skipped = 0;

        for (DesireEntry desire : pendingDesires) {
            if (desire.getIntensity() >= adjustedThreshold) {
                if (isGoodMomentToInitiate(desire)) {
                    log.info("[INITIATIVE] desire={} label={} intensity={} threshold={}/{} decision=TRIGGER",
                            desire.getId(), desire.getLabel(), desire.getIntensity(), baseThreshold, adjustedThreshold);
                    centralFeedBackHandler.handleFeedBack(new FeedBackEvent(UXEventType.INITIATIVE_START));
                    initiateDesireAction(desire);
                    triggered++;
                } else {
                    log.info("[INITIATIVE] desire={} label={} intensity={} threshold={}/{} decision=WRONG_TIME",
                            desire.getId(), desire.getLabel(), desire.getIntensity(), baseThreshold, adjustedThreshold);
                    skipped++;
                }
            } else {
                log.info("[INITIATIVE] desire={} label={} intensity={} threshold={}/{} decision=SKIP",
                        desire.getId(), desire.getLabel(), desire.getIntensity(), baseThreshold, adjustedThreshold);
                skipped++;
            }
        }

        log.info("[INITIATIVE] cycle pending={} triggered={} skipped={}", pendingDesires.size(), triggered, skipped);
    }

    private boolean isGoodMomentToInitiate(DesireEntry desire) {
        // Placeholder for modular logic to determine the right moment.
        // This can be expanded to check for user presence, conversation state, etc.

        if (LocalTime.now().isAfter(LocalTime.MIDNIGHT) && LocalTime.now().isBefore(LocalTime.of(personalityProperties.getInitiativeNoInitiativeUntilHour(), 0))) {
            return false;
        }
        return true;
    }

    private void initiateDesireAction(DesireEntry desire) {
        desireService.withDesireLock(() -> {
            Event<DesireEntry> initiativeEvent = new Event<>(
                    EventType.INITIATIVE,
                    EventPriority.LOW,
                    desire,
                    "DesireInitiativeProducer"
            );

            boolean queued = eventQueue.offer(initiativeEvent);
            if (queued) {
                desire.setStatus(DesireEntry.Status.ACTIVE);
                desireService.storeDesire(desire);
            } else {
                log.warn("Event queue full, could not queue initiative for desire {}", desire.getId());
            }
        });
    }
}

