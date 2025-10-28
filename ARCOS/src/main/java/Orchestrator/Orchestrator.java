package Orchestrator;

import EventBus.EventQueue;
import EventBus.Events.Event;
import EventBus.Events.EventType;
import IO.OuputHandling.PiperEmbeddedTTSModule;
import Memory.LongTermMemory.Models.DesireEntry;
import Memory.LongTermMemory.Models.MemoryEntry;
import Memory.LongTermMemory.Models.SearchResult.SearchResult;
import LLM.LLMClient;
import java.util.List;
import java.util.stream.Collectors;
import Memory.ConversationContext;
import Memory.LongTermMemory.service.MemoryService;
import LLM.Prompts.PromptBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import Personality.PersonalityOrchestrator;


import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class Orchestrator
{
    private final EventQueue eventQueue;
    private final LLMClient llmClient;
    private final PromptBuilder promptBuilder;
    private final ConversationContext context;
    private final MemoryService memoryService;
    private final InitiativeService initiativeService;
    private final PiperEmbeddedTTSModule ttsHandler;
    private final PersonalityOrchestrator personalityOrchestrator;

    private LocalDateTime lastInteracted;

    @Autowired
    public Orchestrator(PersonalityOrchestrator personalityOrchestrator, EventQueue evenQueue, LLMClient llmClient, PromptBuilder promptBuilder, ConversationContext context, MemoryService memoryService, InitiativeService initiativeService) {
        this(personalityOrchestrator, evenQueue, llmClient, promptBuilder, context, memoryService, initiativeService, new PiperEmbeddedTTSModule());
    }

    public Orchestrator(PersonalityOrchestrator personalityOrchestrator, EventQueue evenQueue, LLMClient llmClient, PromptBuilder promptBuilder, ConversationContext context, MemoryService memoryService, InitiativeService initiativeService, PiperEmbeddedTTSModule ttsHandler) {
        this.eventQueue = evenQueue;
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.context = context;
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
                memoryService.storeDesire(desire);
            }
        } else if (event.getType() == EventType.CALENDAR_EVENT_SCHEDULER) {

            ttsHandler.speak(llmClient.generateResponse(promptBuilder.buildSchedulerAlertPrompt((event.getPayload()))));

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


        // Search for relevant memories
        List<MemoryEntry> relevantMemories = memoryService.searchMemories(userQuery)
                .stream()
                .map(SearchResult::getEntry)
                .collect(Collectors.toList());



        String answer = llmClient.generateChatResponse(userQuery);
        log.info("Answer: {}", answer);

        // 7. Ajout à la mémoire à court terme.
        context.addUserMessage(userQuery);
        //context.addAssistantMessage(finalResponse, plan);
        //log.info("Starting personality processing workflow...");
        //triggerPersonalityProcessing(lastInteracted);
        return answer;
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
                    String fullConversation = context.getFullConversation();
                    personalityOrchestrator.processMemory(fullConversation);
                }
            } catch (Exception e) {
                log.error("Error during personality processing", e);
            }
        });

    }
}
