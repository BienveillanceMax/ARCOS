package org.arcos;

import EventLoop.EventLoopRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

//might need to setup time because of dual boot : timedatectl set-time "2014-05-26 11:13:54"
@SpringBootApplication(scanBasePackages = {"LLM", "Orchestrator", "Memory", "Prompts", "org.arcos", "EventLoop", "OrchestratorV2"})
public class ArcosApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(ArcosApplication.class, args);
        EventLoopRunner eventLoopRunner = context.getBean(EventLoopRunner.class);
        eventLoopRunner.run();
    }

}
