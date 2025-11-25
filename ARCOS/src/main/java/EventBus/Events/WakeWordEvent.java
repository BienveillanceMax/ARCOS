package EventBus.Events;

public class WakeWordEvent extends Event<String> {
    private final String userId;

    public WakeWordEvent(String transcription, String userId) {
        super(EventType.WAKEWORD, transcription, "WakeWordProducer");
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }
}
