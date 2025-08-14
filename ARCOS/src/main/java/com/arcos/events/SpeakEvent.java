package com.arcos.events;

public class SpeakEvent extends Event {
    private final String text;

    public SpeakEvent(String text) {
        super(Priority.LOW);
        this.text = text;
    }

    public String getText() {
        return text;
    }
}
