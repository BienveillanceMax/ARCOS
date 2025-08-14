package com.arcos.events;

import java.time.LocalDateTime;

public abstract class Event implements Comparable<Event> {

    public enum Priority {
        HIGH, MEDIUM, LOW
    }

    private final LocalDateTime createdAt;
    private final Priority priority;

    public Event(Priority priority) {
        this.createdAt = LocalDateTime.now();
        this.priority = priority;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Priority getPriority() {
        return priority;
    }

    @Override
    public int compareTo(Event other) {
        return this.priority.compareTo(other.priority);
    }
}
