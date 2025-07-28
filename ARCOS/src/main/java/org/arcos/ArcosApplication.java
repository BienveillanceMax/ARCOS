package org.arcos;

import EventLoop.EventLoopRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ArcosApplication
{

    public static void main(String[] args) {
        SpringApplication.run(ArcosApplication.class, args);

        EventLoopRunner eventLoopRunner = new EventLoopRunner();
        eventLoopRunner.run();
        //WakeWordDetector.showAudioDevices();
    }

}
