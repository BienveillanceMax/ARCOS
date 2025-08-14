package org.arcos;

import EventLoop.EventLoopRunner;
import EventLoop.InputHandling.WakeWordDetector;
import Orchestrator.Orchestrator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;


//might need to setup time because of dual boot : timedatectl set-time "2014-05-26 11:13:54"
@SpringBootApplication(scanBasePackages = {"LLM", "Orchestrator", "Memory", "Prompts", "org.arcos"})
public class ArcosApplication
{

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(ArcosApplication.class, args);

        //EventLoopRunner eventLoopRunner = new EventLoopRunner();
        //eventLoopRunner.run();
        //WakeWordDetector.showAudioDevices();
        Orchestrator orchestrator = context.getBean(Orchestrator.class);
        //System.out.println(orchestrator.processQuery("Je suis ton créateur, quelles actions et fonctionnalités voudrais-tu que je te rajoute ?"));
        //System.out.println(orchestrator.processQuery("Te rappelle-tu de la question que je t'ai posé précédemment ?"));
        //EventLoopRunner eventLoopRunner = new EventLoopRunner(orchestrator);
        //eventLoopRunner.run();

        //WakeWordDetector.showAudioDevices();
    }

}
