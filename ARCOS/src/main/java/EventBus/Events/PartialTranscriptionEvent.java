package EventBus.Events;

public class PartialTranscriptionEvent extends Event<String> {
    public PartialTranscriptionEvent(String payload, String source) {
        super(EventType.PARTIAL_TRANSCRIPTION, EventPriority.LOW, payload, source);
    }
}