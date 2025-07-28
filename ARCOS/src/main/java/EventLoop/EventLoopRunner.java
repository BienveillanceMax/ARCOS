package EventLoop;

import EventLoop.InputHandling.WakeWordDetector;
import EventLoop.OuputHandling.PiperEmbeddedTTSModule;
import EventLoop.OuputHandling.TTSHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class EventLoopRunner
{
    private WakeWordDetector wakeWordDetector;
    private TTSHandler ttsHandler;
    @Autowired
    public EventLoopRunner() {
        this.wakeWordDetector = new WakeWordDetector();
        this.ttsHandler = new TTSHandler();
    }

    public void run() {

        this.ttsHandler.initialize();
        ttsHandler.speak("Bonjour à tous, je suis la voix par défaut de Arcöss !");

        while (true) {
            //String message = this.wakeWordDetector.startRecording();
        }
    }
}

