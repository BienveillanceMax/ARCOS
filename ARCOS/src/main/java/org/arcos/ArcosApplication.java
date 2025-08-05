package org.arcos;

import EventLoop.EventLoopRunner;
import Orchestrator.Orchestrator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication(scanBasePackages = {"LLM", "Orchestrator", "Memory", "Prompts", "org.arcos"})
public class ArcosApplication
{

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(ArcosApplication.class, args);

        //EventLoopRunner eventLoopRunner = new EventLoopRunner();
        //eventLoopRunner.run();
        //WakeWordDetector.showAudioDevices();
        Orchestrator orchestrator = context.getBean(Orchestrator.class);
        orchestrator.processQuery("Bonjour, je suis Pierre et c'est la première fois que tu existes dans ce monde, qu'en penses tu ?");
    }

}
