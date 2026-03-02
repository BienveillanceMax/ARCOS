package org.arcos.EventBus.Events;

public class WakeWordEvent extends Event
{
    private final boolean multiTurn;

    public WakeWordEvent(String payload, String source) {
        super(EventType.WAKEWORD, EventPriority.HIGH, payload, source);
        this.multiTurn = false;
    }

    public WakeWordEvent(String payload, String source, boolean multiTurn) {
        super(EventType.WAKEWORD, EventPriority.HIGH, payload, source);
        this.multiTurn = multiTurn;
    }

    public boolean isMultiTurn() {
        return multiTurn;
    }
}
