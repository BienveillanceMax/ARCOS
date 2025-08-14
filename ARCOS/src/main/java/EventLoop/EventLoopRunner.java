package EventLoop;

import EventLoop.InputHandling.WakeWordDetector;
import OrchestratorV2.Entities.Event;
import OrchestratorV2.Entities.EventType;
import OrchestratorV2.OrchestratorV2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EventLoopRunner {
    private final WakeWordDetector wakeWordDetector;
    private final OrchestratorV2 orchestratorV2;

    @Autowired
    public EventLoopRunner(OrchestratorV2 orchestratorV2) {
        this.wakeWordDetector = new WakeWordDetector();
        this.orchestratorV2 = orchestratorV2;
    }

    public void run() {
        while (true) {
            wakeWordDetector.startRecording();
            orchestratorV2.publishEvent(new Event<>(EventType.WAKE_WORD_DETECTED, null));
        }
    }
}

