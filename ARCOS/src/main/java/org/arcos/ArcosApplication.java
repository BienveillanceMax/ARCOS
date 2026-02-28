package org.arcos;

import org.arcos.Boot.Banner.AsciiBanner;
import org.arcos.IO.OuputHandling.StateHandler.CentralFeedBackHandler;
import org.arcos.IO.OuputHandling.StateHandler.UXEventType;
import org.arcos.IO.OuputHandling.StateHandler.FeedBackEvent;
import org.arcos.Orchestrator.Orchestrator;
import org.arcos.Setup.WizardRunner;
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
        // Bannière ASCII avant le wizard et le démarrage Spring
        AsciiBanner.print();

        // STORY-001 : détection automatique de configuration manquante (pre-Spring)
        // STORY-023 : relancement du wizard via --setup / --reconfigure
        WizardRunner.runIfNeeded(args);

        ConfigurableApplicationContext context = SpringApplication.run(ArcosApplication.class, args);
        Orchestrator orchestrator = context.getBean(Orchestrator.class);
        CentralFeedBackHandler centralFeedBackHandler = context.getBean(CentralFeedBackHandler.class);
        centralFeedBackHandler.handleFeedBack(new FeedBackEvent(UXEventType.ARCOS_START));
        orchestrator.start();
    }

}
