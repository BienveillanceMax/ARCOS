package OrchestratorV2;

import EventLoop.InputHandling.AudioForwarder;
import Orchestrator.Orchestrator;
import OrchestratorV2.Entities.Event;
import OrchestratorV2.Entities.EventType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import javax.annotation.PostConstruct;

@Component
public class OrchestratorV2 {

    private final Sinks.Many<Event<?>> eventQueue = Sinks.many().multicast().onBackpressureBuffer();
    private final AudioForwarder audioForwarder;
    private final Orchestrator orchestrator;

    @Autowired
    public OrchestratorV2(AudioForwarder audioForwarder, Orchestrator orchestrator) {
        this.audioForwarder = audioForwarder;
        this.orchestrator = orchestrator;
    }

    public void publishEvent(Event<?> event) {
        eventQueue.tryEmitNext(event);
    }

    public Flux<Event<?>> getEventStream() {
        return eventQueue.asFlux();
    }

    @PostConstruct
    public void processEvents() {
        getEventStream()
                .publishOn(Schedulers.boundedElastic())
                .subscribe(this::handleEvent);
    }

    private void handleEvent(Event<?> event) {
        switch (event.getType()) {
            case WAKE_WORD_DETECTED:
                handleWakeWordDetected();
                break;
            case USER_QUERY_CAPTURED:
                handleUserQueryCaptured((String) event.getPayload());
                break;
            case ASSISTANT_RESPONSE_GENERATED:
                // Handle assistant response
                break;
            case ERROR:
                // Handle error
                break;
        }
    }

    private void handleWakeWordDetected() {
        System.out.println("Wake word detected! Now capturing user query...");
        audioForwarder.startForwarding().thenAccept(query -> {
            publishEvent(new Event<>(EventType.USER_QUERY_CAPTURED, query));
        });
    }

    private void handleUserQueryCaptured(String query) {
        System.out.println("User query captured: " + query);
        String response = orchestrator.processQuery(query);
        publishEvent(new Event<>(EventType.ASSISTANT_RESPONSE_GENERATED, response));
    }
}
