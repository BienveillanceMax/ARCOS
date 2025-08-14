package OrchestratorV2.Entities;

import lombok.Getter;

@Getter
public class Event<T> {
    private final EventType type;
    private final T payload;

    public Event(EventType type, T payload) {
        this.type = type;
        this.payload = payload;
    }

}
