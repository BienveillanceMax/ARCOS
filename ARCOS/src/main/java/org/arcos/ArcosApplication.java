package org.arcos;

import EventBus.EventQueue;
import Memory.LongTermMemory.Models.DesireEntry;
import Memory.LongTermMemory.Models.Subject;
import Memory.LongTermMemory.Qdrant.QdrantClient;
import Memory.LongTermMemory.service.MemoryService;
import Orchestrator.Orchestrator;
import Personality.Desires.DesireService;
import Producers.WakeWordProducer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;


//might need to setup time because of dual boot : timedatectl set-time "2014-05-26 11:13:54"
@SpringBootApplication(scanBasePackages = {"EventBus", "Producers", "LLM", "Orchestrator", "Memory", "org.arcos", "Personality", "Tools"})
@EnableScheduling
public class ArcosApplication
{

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(ArcosApplication.class, args);


        Orchestrator orchestrator = context.getBean(Orchestrator.class);
        orchestrator.start();
        //EventLoopRunner eventLoopRunner = new EventLoopRunner();
        //eventLoopRunner.run();
        DesireService desireService = context.getBean(DesireService.class);
        MemoryService memoryService = context.getBean(MemoryService.class);
        DesireEntry desireEntry = new DesireEntry();
        desireEntry.setStatus(DesireEntry.Status.PENDING);
        desireEntry.setIntensity(0.9);
        desireEntry.setDescription("J'aimerai mieux comprendre ce monde");

        //memoryService.storeDesire(desireEntry);

        //WakeWordProducer.showAudioDevices();

        //EventQueue queue = context.getBean(EventQueue.class);
        //Orchestrator orchestrator = context.getBean(Orchestrator.class);
        //orchestrator.start();

        //System.out.println(orchestrator.processQuery("Je suis ton créateur, quelles actions et fonctionnalités voudrais-tu que je te rajoute ?"));
        //System.out.println(orchestrator.processQuery("Te rappelle-tu de la question que je t'ai posé précédemment ?"));
        //EventLoopRunner eventLoopRunner = new EventLoopRunner(orchestrator);
        //eventLoopRunner.run();

        WakeWordProducer.showAudioDevices();
    }

}
