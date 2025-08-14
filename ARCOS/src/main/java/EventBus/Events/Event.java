package EventBus.Events;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Classe générique représentant un événement dans le système EventBus
 * @param <T> Type de données transportées par l'événement
 */
public class Event<T> implements Comparable<Event<?>> {
    private final String id;
    private final EventType type;
    private final EventPriority priority;
    private final T payload;
    private final LocalDateTime timestamp;
    private final String source;

    public Event(EventType type, EventPriority priority, T payload, String source) {
        this.id = UUID.randomUUID().toString();
        this.type = type;
        this.priority = priority;
        this.payload = payload;
        this.source = source;
        this.timestamp = LocalDateTime.now();
    }

    public Event(EventType type, T payload, String source) {
        this(type, EventPriority.MEDIUM, payload, source);
    }

    @Override
    public int compareTo(Event<?> other) {
        // Priorité plus élevée = valeur plus petite pour PriorityQueue
        return this.priority.compareTo(other.priority);
    }

    // Getters
    public String getId() { return id; }
    public EventType getType() { return type; }
    public EventPriority getPriority() { return priority; }
    public T getPayload() { return payload; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getSource() { return source; }

    @Override
    public String toString() {
        return String.format("Event{id='%s', type=%s, priority=%s, source='%s', timestamp=%s}",
                id, type, priority, source, timestamp);
    }
}
