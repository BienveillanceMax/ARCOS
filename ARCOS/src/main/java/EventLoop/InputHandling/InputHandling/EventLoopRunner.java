package EventLoop.InputHandling.InputHandling;

import EventLoop.InputHandling.OutputHandling.TextToSpeech;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class EventLoopRunner
{
    private final WakeWordDetector wakeWordDetector;
    private final TextToSpeech textToSpeech;

    @Autowired
    public EventLoopRunner() {
        this.wakeWordDetector = new WakeWordDetector();
        this.textToSpeech = new TextToSpeech();
    }

    public void run() {

        while (true) {
            String message = this.wakeWordDetector.startRecording();
            if (message != null && !message.isEmpty()) {
                this.textToSpeech.speak(message);
            }
        }
    }
}

