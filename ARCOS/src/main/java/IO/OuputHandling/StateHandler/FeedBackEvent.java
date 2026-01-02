package IO.OuputHandling.StateHandler;

public class FeedBackEvent
{
    public FeedBackEvent(EventType eventType) {
        this.eventType = eventType;
    }

    private final EventType eventType;

    public EventType getEventType() {
        return eventType;
    }
}