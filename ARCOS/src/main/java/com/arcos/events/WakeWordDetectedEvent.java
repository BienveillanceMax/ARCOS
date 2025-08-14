package com.arcos.events;

public class WakeWordDetectedEvent extends Event {
    public WakeWordDetectedEvent() {
        super(Priority.HIGH);
    }
}
