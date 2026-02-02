package org.arcos;

import org.arcos.EventBus.EventQueue;
import org.arcos.EventBus.Events.Event;
import org.arcos.EventBus.Events.EventType;
import org.arcos.IO.OuputHandling.StateHandler.CentralFeedBackHandler;
import org.arcos.IO.OuputHandling.StateHandler.UXEventType;
import org.arcos.IO.OuputHandling.StateHandler.FeedBackEvent;
import org.arcos.Memory.LongTermMemory.Models.MemoryEntry;
import org.arcos.Memory.LongTermMemory.Models.OpinionEntry;
import org.arcos.Memory.LongTermMemory.Models.Subject;
import org.arcos.Orchestrator.Orchestrator;
import lombok.extern.slf4j.Slf4j;
import org.arcos.Personality.Opinions.OpinionService;
import org.arcos.Personality.Values.Entities.DimensionSchwartz;
import org.arcos.Tools.Actions.ActionResult;
import org.arcos.Tools.Actions.SearchActions;
import org.arcos.Tools.SearchTool.BraveSearchService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

//might need to setup time because of dual boot : timedatectl set-time "2014-05-26 11:13:54"
@Slf4j
@SpringBootApplication
@EnableScheduling
public class ArcosApplication
{

    public static void main(String[] args) {


        //System.out.println("ArcosApplication available audio devices : ");
        //WakeWordProducer.showAudioDevices();
        //System.out.println("\n");


        ConfigurableApplicationContext context = SpringApplication.run(ArcosApplication.class, args);
        Orchestrator orchestrator = context.getBean(Orchestrator.class);


        //PiperEmbeddedTTSModule  piperEmbeddedTTSModule = new PiperEmbeddedTTSModule();
        EventQueue eventQueue = context.getBean(EventQueue.class);
        eventQueue.offer(new Event<>(EventType.WAKEWORD,"J'ai besoin de me changer les idées, organise moi une sortie demain à 14H.","home"));
        //eventQueue.offer(new Event<>(EventType.WAKEWORD,"Que veux-tu et que veux tu devenir ?","home"));
        CentralFeedBackHandler centralFeedBackHandler = context.getBean(CentralFeedBackHandler.class);
        centralFeedBackHandler.handleFeedBack(new FeedBackEvent(UXEventType.ARCOS_START));
        OpinionService opinionService = context.getBean(OpinionService.class);
        OpinionEntry opinionEntry = createOpinionEntry();


        //SearchActions searchActions = context.getBean(SearchActions.class);
        //HashMap<String, Object> map = new HashMap<>();
        //map.put("query", "actualité");
        //ActionResult actionResult = searchActions.searchTheWeb(map);
        //System.out.println(actionResult.getData().toString());

        orchestrator.start();



        /*try {
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


        //memoryService.storeDesire(desireEntry);

        //WakeWordProducer.showAudioDevices();

        //EventQueue queue = context.getBean(EventQueue.class);
        //Orchestrator orchestrator = context.getBean(Orchestrator.class);
        //orchestrator.start();

        //System.out.println(orchestrator.processQuery("Je suis ton créateur, quelles actions et fonctionnalités voudrais-tu que je te rajoute ?"));
        //System.out.println(orchestrator.processQuery("Te rappelle-tu de la question que je t'ai posé précédemment ?"));
        //eventLoopRunner.run();

         */
    }
    public static void printAudioMixers() {
        System.out.println("--- Recherche des périphériques Audio ---");
        javax.sound.sampled.Mixer.Info[] mixers = javax.sound.sampled.AudioSystem.getMixerInfo();
        if (mixers.length == 0) {
            System.err.println("AUCUN PÉRIPHÉRIQUE AUDIO TROUVÉ !");
        }
        for (javax.sound.sampled.Mixer.Info mixerInfo : mixers) {
            System.out.println("Mixer: " + mixerInfo.getName() + " [" + mixerInfo.getDescription() + "]");
        }
        System.out.println("-----------------------------------------");
    }

    public static OpinionEntry createOpinionEntry() {
        OpinionEntry createdOpinion = new OpinionEntry();
        createdOpinion.setId(UUID.randomUUID().toString());
        createdOpinion.setSubject("Existing Subject");
        createdOpinion.setStability(1);
        createdOpinion.setCreatedAt(LocalDateTime.now());
        createdOpinion.setUpdatedAt(LocalDateTime.now());
        createdOpinion.setAssociatedMemories(new ArrayList<>());
        createdOpinion.setSummary("On devrait tous s'aimer");
        createdOpinion.setNarrative("et l'univers t'a dit « je t'aime » et l'univers t'a dit « tu as bien joué le jeu »et l'univers t'a dit « tout ce dont tu as besoin est en toi »et l'univers t'a dit « tu es plus fort que tu ne le penses »et l'univers t'a dit « tu es la lumière du jour »et l'univers t'a dit « tu es la nuit »et l'univers t'a dit « les ténèbres que tu combats sont en toi »et l'univers t'a dit « la lumière que tu cherches est en toi »et l'univers t'a dit « tu n'es pas seul »et l'univers a dit que tu n'es pas séparé de tout le reste et l'univers a dit que tu es l'univers qui se goûte lui-même, qui se parle à lui-même, qui lit son propre code et l'univers a dit « je t'aime parce que tu es amour ».");
        createdOpinion.setPolarity(0.5);
        createdOpinion.setConfidence(1);
        createdOpinion.setAssociatedDesire("");
        createdOpinion.setMainDimension(DimensionSchwartz.CONSERVATION);
        createdOpinion.setEmbedding(new float[1024]);
        return createdOpinion;
    }

}