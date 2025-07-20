package EventLoop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ai.picovoice.porcupine.*;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Component
public class EventLoopRunner
{
    WakeWordDetector wakeWordDetector;

    @Autowired
    public EventLoopRunner() {
        this.wakeWordDetector = new WakeWordDetector();
    }



    public void EventLoop() {
        wakeWordDetector.startRecording();
    }
}
