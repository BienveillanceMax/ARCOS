package Orchestrator;

import EventBus.EventQueue;
import EventBus.Events.Event;
import EventBus.Events.EventType;
import Exceptions.ResponseParsingException;
import IO.OuputHandling.PiperEmbeddedTTSModule;
import Memory.LongTermMemory.Models.DesireEntry;
import org.springframework.ai.chat.client.ChatClient;
import Memory.LongTermMemory.Models.MemoryEntry;
import Memory.LongTermMemory.Models.SearchResult.SearchResult;
import LLM.LLMClient;
import java.util.List;
import java.util.stream.Collectors;
import Memory.Actions.Entities.ActionResult;
import org.springframework.ai.chat.memory.ChatMemory;
import Memory.LongTermMemory.service.MemoryService;
import Orchestrator.Entities.ExecutionPlan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cglib.core.Local;
import org.springframework.stereotype.Component;
import Personality.PersonalityOrchestrator;


import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class Orchestrator
{
    private final EventQueue eventQueue;
    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final MemoryService memoryService;
    private final InitiativeService initiativeService;
    private final PiperEmbeddedTTSModule ttsHandler;
    private final PersonalityOrchestrator personalityOrchestrator;

    private LocalDateTime lastInteracted;

    @Autowired
    public Orchestrator(PersonalityOrchestrator personalityOrchestrator, EventQueue evenQueue, ChatClient chatClient, ChatMemory chatMemory, MemoryService memoryService, InitiativeService initiativeService) {
        this(personalityOrchestrator, evenQueue, chatClient, chatMemory, memoryService, initiativeService, new PiperEmbeddedTTSModule());
    }

    public Orchestrator(PersonalityOrchestrator personalityOrchestrator, EventQueue evenQueue, ChatClient chatClient, ChatMemory chatMemory, MemoryService memoryService, InitiativeService initiativeService, PiperEmbeddedTTSModule ttsHandler) {
        this.eventQueue = evenQueue;
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
        this.memoryService = memoryService;
        this.initiativeService = initiativeService;
        this.personalityOrchestrator = personalityOrchestrator;
        this.ttsHandler = ttsHandler;
        lastInteracted = LocalDateTime.now();
    }


    public void dispatch(Event<?> event) {
        if (event.getType() == EventType.WAKEWORD) {
            log.info("starting processing");
            ttsHandler.speak(processQuery((String) event.getPayload()));
        } else if (event.getType() == EventType.INITIATIVE) {
            DesireEntry desire = (DesireEntry) event.getPayload();
            try {
                initiativeService.processInitiative(desire);
            } catch (Exception e) {
                log.error("A critical error occurred in InitiativeService, reverting desire status for {}", desire.getId(), e);
                desire.setStatus(DesireEntry.Status.PENDING);
                desire.setLastUpdated(java.time.LocalDateTime.now());
            }
        } else if (event.getType() == EventType.CALENDAR_EVENT_SCHEDULER) {

            ttsHandler.speak("Calendar event scheduler is not implemented yet.");

        }
    }

    public void start() {
        while (true) {
            try {
                Event<?> event = eventQueue.take();
                dispatch(event);
            } catch (InterruptedException e) {
                log.error("Event queue was interrupted", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    private String processQuery(String userQuery) {
        log.info("Processing query: {}", userQuery);

        return chatClient.prompt()
                .user(userQuery)
                .call()
                .content();
    }

    private void triggerPersonalityProcessing(LocalDateTime lastInteraction) {
        CompletableFuture.runAsync(() -> {
            try {
                // On ne veut pas que la personnalité se déclenche à chaque interaction.
                Duration elapsedTime = Duration.between(lastInteraction, LocalDateTime.now());
                log.info("Personality Processing : waiting to avoid usage limit");
                Thread.sleep(1000);
                if (elapsedTime.toMinutes() > 10) {
                    log.info("Triggering personality processing...");
                    String fullConversation = chatMemory.toString();
                    personalityOrchestrator.processMemory(fullConversation);
                }
            } catch (Exception e) {
                log.error("Error during personality processing", e);
            }
        });

    }
}
