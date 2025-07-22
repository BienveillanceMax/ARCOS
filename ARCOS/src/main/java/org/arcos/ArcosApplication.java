package org.arcos;

import EventLoop.InputHandling.EventLoopRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.apache.logging.log4j.util.LoaderUtil.getClassLoader;

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
