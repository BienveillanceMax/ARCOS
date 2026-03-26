package org.arcos.Producers;

import lombok.extern.slf4j.Slf4j;
import org.arcos.EventBus.EventQueue;
import org.arcos.EventBus.Events.Event;
import org.arcos.EventBus.Events.EventPriority;
import org.arcos.EventBus.Events.EventType;
import org.arcos.UserModel.UserModelProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Producteur multi-signal basé sur l'inactivité utilisateur.
 * Émet SESSION_END après une courte inactivité (fin de conversation)
 * et IDLE_WINDOW_OPEN après une longue inactivité (fenêtre pour le batch pipeline).
 */
@Slf4j
@Component
@EnableScheduling
public class InactivityProducer {

    private final EventQueue eventQueue;
    private final int sessionEndThresholdMinutes;
    private final int idleWindowThresholdMinutes;

    private final Object stateLock = new Object();
    private LocalDateTime lastInteractionTime;
    private boolean sessionActive = false;
    private boolean idleWindowEmitted = false;
    private boolean hasHadSession = false;

    public InactivityProducer(EventQueue eventQueue, UserModelProperties properties) {
        this.eventQueue = eventQueue;
        this.sessionEndThresholdMinutes = properties.getSessionEndThresholdMinutes();
        this.idleWindowThresholdMinutes = properties.getIdleThresholdMinutes();
        if (sessionEndThresholdMinutes >= idleWindowThresholdMinutes) {
            throw new IllegalArgumentException(
                    "sessionEndThresholdMinutes (" + sessionEndThresholdMinutes +
                    ") must be < idleWindowThresholdMinutes (" + idleWindowThresholdMinutes + ")");
        }
        this.lastInteractionTime = LocalDateTime.now();
    }

    public void recordInteraction() {
        synchronized (stateLock) {
            this.lastInteractionTime = LocalDateTime.now();
            this.sessionActive = true;
            this.idleWindowEmitted = false;
            this.hasHadSession = true;
        }
        log.debug("Interaction recorded at {}", lastInteractionTime);
    }

    public boolean isIdle() {
        return getIdleDuration().toMinutes() >= idleWindowThresholdMinutes;
    }

    public Duration getIdleDuration() {
        synchronized (stateLock) {
            return Duration.between(lastInteractionTime, LocalDateTime.now());
        }
    }

    @Scheduled(fixedDelayString = "${arcos.inactivity.check-interval-ms:60000}")
    public void checkInactivity() {
        synchronized (stateLock) {
            if (!sessionActive && idleWindowEmitted) {
                return;
            }
            long inactiveMinutes = getIdleDuration().toMinutes();

            if (sessionActive && inactiveMinutes >= sessionEndThresholdMinutes) {
                sessionActive = false;
                eventQueue.offer(new Event<>(
                        EventType.SESSION_END, EventPriority.LOW, null, "InactivityProducer"));
                log.info("SESSION_END emitted after {}min inactivity", inactiveMinutes);
            }

            if (!idleWindowEmitted && hasHadSession && inactiveMinutes >= idleWindowThresholdMinutes) {
                idleWindowEmitted = true;
                eventQueue.offer(new Event<>(
                        EventType.IDLE_WINDOW_OPEN, EventPriority.LOW, null, "InactivityProducer"));
                log.info("IDLE_WINDOW_OPEN emitted after {}min inactivity", inactiveMinutes);
            }
        }
    }
}
