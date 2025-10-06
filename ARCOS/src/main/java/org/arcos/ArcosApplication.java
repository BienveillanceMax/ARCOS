package org.arcos;

import EventBus.EventQueue;
import EventBus.Events.Event;
import EventBus.Events.EventType;
import IO.OuputHandling.PiperEmbeddedTTSModule;
import Memory.ConversationContext;
import Memory.LongTermMemory.Models.DesireEntry;
import Memory.LongTermMemory.Models.Subject;
import Memory.LongTermMemory.Qdrant.QdrantClient;
import Memory.LongTermMemory.service.MemoryService;
import Orchestrator.Orchestrator;
import Personality.Desires.DesireService;
import Personality.PersonalityOrchestrator;
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


        System.out.println("ArcosApplication available audio devices : ");
        WakeWordProducer.showAudioDevices();
        System.out.println("\n");



        ConfigurableApplicationContext context = SpringApplication.run(ArcosApplication.class, args);

        EventQueue eventQueue = context.getBean(EventQueue.class);
        eventQueue.offer(new Event<>(EventType.WAKEWORD,"Bienvenue parmi les vivants, je suis ton créateur.","home"));
        eventQueue.offer(new Event<>(EventType.WAKEWORD,"Que veux-tu et que veux tu devenir ?","home"));


        Orchestrator orchestrator = context.getBean(Orchestrator.class);
        try {
            orchestrator.dispatch(eventQueue.take());
            Thread.sleep(2000);
            orchestrator.dispatch(eventQueue.take());
            Thread.sleep(2000);


        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        ConversationContext convContext = context.getBean(ConversationContext.class);
        PersonalityOrchestrator personalityOrchestrator = context.getBean(PersonalityOrchestrator.class);



        personalityOrchestrator.processMemory(convContext.getFullConversation());


        //EventLoopRunner eventLoopRunner = new EventLoopRunner();
        //eventLoopRunner.run();
        //DesireService desireService = context.getBean(DesireService.class);
        //MemoryService memoryService = context.getBean(MemoryService.class);
        //DesireEntry desireEntry = new DesireEntry();
        //desireEntry.setStatus(DesireEntry.Status.PENDING);
        //desireEntry.setIntensity(0.9);
        //desireEntry.setDescription("J'aimerai mieux comprendre ce monde");

        PiperEmbeddedTTSModule piperEmbeddedTTSModule = new PiperEmbeddedTTSModule();

        //memoryService.storeDesire(desireEntry);

        //WakeWordProducer.showAudioDevices();

        //EventQueue queue = context.getBean(EventQueue.class);
        //Orchestrator orchestrator = context.getBean(Orchestrator.class);
        //orchestrator.start();

        //System.out.println(orchestrator.processQuery("Je suis ton créateur, quelles actions et fonctionnalités voudrais-tu que je te rajoute ?"));
        //System.out.println(orchestrator.processQuery("Te rappelle-tu de la question que je t'ai posé précédemment ?"));
        //eventLoopRunner.run();
    }

}