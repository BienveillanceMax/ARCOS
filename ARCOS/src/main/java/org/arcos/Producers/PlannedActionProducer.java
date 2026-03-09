package org.arcos.Producers;

import lombok.extern.slf4j.Slf4j;
import org.arcos.EventBus.EventQueue;
import org.arcos.EventBus.Events.Event;
import org.arcos.EventBus.Events.EventPriority;
import org.arcos.EventBus.Events.EventType;
import org.arcos.PlannedAction.Models.PlannedActionEntry;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class PlannedActionProducer {

    private final EventQueue eventQueue;

    public PlannedActionProducer(EventQueue eventQueue) {
        this.eventQueue = eventQueue;
    }

    public void onActionTriggered(PlannedActionEntry entry) {
        log.info("Planned action triggered: {}", entry.getLabel());
        entry.setLastExecutedAt(LocalDateTime.now());
        entry.setExecutionCount(entry.getExecutionCount() + 1);

        Event<PlannedActionEntry> event = new Event<>(
                EventType.PLANNED_ACTION,
                EventPriority.MEDIUM,
                entry,
                "PlannedActionProducer"
        );

        boolean offered = eventQueue.offer(event);
        if (!offered) {
            log.warn("EventQueue full, could not enqueue planned action: {}", entry.getLabel());
        }
    }

    public void onReminderTriggered(PlannedActionEntry entry) {
        log.info("Deadline reminder triggered: {}", entry.getLabel());
        entry.setReminderTrigger(true);

        Event<PlannedActionEntry> event = new Event<>(
                EventType.PLANNED_ACTION,
                EventPriority.MEDIUM,
                entry,
                "PlannedActionProducer"
        );

        boolean offered = eventQueue.offer(event);
        if (!offered) {
            log.warn("EventQueue full, could not enqueue reminder for: {}", entry.getLabel());
        }
    }
}
