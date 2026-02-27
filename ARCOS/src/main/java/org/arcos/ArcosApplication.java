package org.arcos;

import org.arcos.IO.OuputHandling.StateHandler.CentralFeedBackHandler;
import org.arcos.IO.OuputHandling.StateHandler.UXEventType;
import org.arcos.IO.OuputHandling.StateHandler.FeedBackEvent;
import org.arcos.Orchestrator.Orchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

//might need to setup time because of dual boot : timedatectl set-time "2014-05-26 11:13:54"
@Slf4j
@SpringBootApplication
@EnableScheduling
public class ArcosApplication
{

    public static void main(String[] args) {
        validateEnvironment();
        ConfigurableApplicationContext context = SpringApplication.run(ArcosApplication.class, args);
        Orchestrator orchestrator = context.getBean(Orchestrator.class);
        CentralFeedBackHandler centralFeedBackHandler = context.getBean(CentralFeedBackHandler.class);
        centralFeedBackHandler.handleFeedBack(new FeedBackEvent(UXEventType.ARCOS_START));
        orchestrator.start();
    }

    private static void validateEnvironment() {
        if (System.getenv("MISTRALAI_API_KEY") == null || System.getenv("MISTRALAI_API_KEY").isBlank()) {
            log.error("MISTRALAI_API_KEY is not set — the LLM will not function. Set it in your .env file.");
        }
        if (System.getenv("BRAVE_SEARCH_API_KEY") == null || System.getenv("BRAVE_SEARCH_API_KEY").isBlank()) {
            log.warn("BRAVE_SEARCH_API_KEY is not set — internet search (Chercher_sur_Internet) will not be available.");
        }
        if (System.getenv("PORCUPINE_ACCESS_KEY") == null || System.getenv("PORCUPINE_ACCESS_KEY").isBlank()) {
            log.warn("PORCUPINE_ACCESS_KEY is not set — wake word detection will not start.");
        }
    }

}
