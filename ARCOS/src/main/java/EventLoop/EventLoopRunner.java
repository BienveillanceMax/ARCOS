package EventLoop;

import EventLoop.InputHandling.WakeWordDetector;
import EventLoop.OuputHandling.PiperEmbeddedTTSModule;
import EventLoop.OuputHandling.TTSHandler;
import Orchestrator.ActionExecutor;
import Orchestrator.Orchestrator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class EventLoopRunner
{
    private WakeWordDetector wakeWordDetector;
    private TTSHandler ttsHandler;
    private Orchestrator orchestrator;

    @Autowired
    public EventLoopRunner(Orchestrator orchestrator) {
        this.wakeWordDetector = new WakeWordDetector();
        this.ttsHandler = new TTSHandler();
        this.orchestrator = orchestrator;
    }

    public void run() {




        while (true) {
            this.ttsHandler.initialize();
            String query = this.wakeWordDetector.startRecording();
            String answer = orchestrator.processQuery(query);
            ttsHandler.speak(answer);
        }
    }
}

