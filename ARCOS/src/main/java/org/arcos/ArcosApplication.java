package org.arcos;

import EventLoop.EventLoopRunner;
import Orchestrator.Orchestrator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ArcosApplication
{

    public static void main(String[] args) {
        SpringApplication.run(ArcosApplication.class, args);

        //EventLoopRunner eventLoopRunner = new EventLoopRunner();
        //eventLoopRunner.run();
        //WakeWordDetector.showAudioDevices();
        Orchestrator orchestrator = new Orchestrator();
        orchestrator.processQuery("Bonjour, je suis Pierre et c'est la première fois que tu existes dans ce monde, qu'en penses tu ?");
    }

}
