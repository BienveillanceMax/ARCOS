package org.arcos;

import EventLoop.InputHandling.InputHandling.EventLoopRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication(scanBasePackages = "com.arcos.service")
public class ArcosApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(ArcosApplication.class, args);
        EventLoopRunner eventLoopRunner = context.getBean(EventLoopRunner.class);
        eventLoopRunner.run();
    }
}
