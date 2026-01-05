package org.arcos.EventBus.Events;

public class WakeWordEvent extends Event
{
    public WakeWordEvent(String payload, String source) {
        super(EventType.WAKEWORD, EventPriority.HIGH, payload, source);
    }


}
