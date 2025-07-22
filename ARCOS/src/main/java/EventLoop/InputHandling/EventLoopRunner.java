package EventLoop.InputHandling;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class EventLoopRunner
{
    private WakeWordDetector wakeWordDetector;

    @Autowired
    public EventLoopRunner() {
        this.wakeWordDetector = new WakeWordDetector();
    }

    public void run() {

        while (true) {
            String message = this.wakeWordDetector.startRecording();
            System.out.println(message); //placeholder for processing

        }
    }
}

