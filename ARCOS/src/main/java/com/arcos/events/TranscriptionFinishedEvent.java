package com.arcos.events;

public class TranscriptionFinishedEvent extends Event {
    public TranscriptionFinishedEvent() {
        super(Priority.HIGH);
    }
}
