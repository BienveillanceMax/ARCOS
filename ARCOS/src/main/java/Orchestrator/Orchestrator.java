package Orchestrator;

import EventBus.EventQueue;
import EventBus.Events.Event;
import EventBus.Events.EventType;
import Exceptions.ResponseParsingException;
import IO.OuputHandling.TTSHandler;
import LLM.LLMResponseParser;
import Memory.LongTermMemory.Models.DesireEntry;
import LLM.LLMClient;
import Memory.ConversationContext;
import Memory.Actions.Entities.ActionResult;
import Memory.LongTermMemory.service.MemoryService;
import Orchestrator.Entities.ExecutionPlan;
import LLM.Prompts.PromptBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final LLMClient llmClient;
    private final PromptBuilder promptBuilder;
    private final LLMResponseParser responseParser;
    private final ActionExecutor actionExecutor;
    private final ConversationContext context;
    private final MemoryService memoryService;
    private final InitiativeService initiativeService;
    private final TTSHandler ttsHandler;
    private final PersonalityOrchestrator personalityOrchestrator;

    private LocalDateTime lastInteracted;

    @Autowired
    public Orchestrator(PersonalityOrchestrator personalityOrchestrator, EventQueue evenQueue, LLMClient llmClient, PromptBuilder promptBuilder, LLMResponseParser responseParser, ActionExecutor actionExecutor, ConversationContext context, MemoryService memoryService, InitiativeService initiativeService) {
        this.eventQueue = evenQueue;
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.responseParser = responseParser;
        this.actionExecutor = actionExecutor;
        this.context = context;
        this.memoryService = memoryService;
        this.initiativeService = initiativeService;
        this.personalityOrchestrator = personalityOrchestrator;
        lastInteracted = LocalDateTime.now();

        this.ttsHandler = new TTSHandler();
        this.ttsHandler.start();
    }


    private void dispatch(Event<?> event) {
        if (event.getType() == EventType.WAKEWORD) {

            TTSHandler ttsHandler = new TTSHandler();
            ttsHandler.initialize();
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
        // 1. Génération du prompt
        String planningPrompt = promptBuilder.buildPlanningPrompt(userQuery, context);
        log.debug("Planning prompt:\n{}", planningPrompt);

        // 2. Appel à Mistral pour planification
        String planningResponse = llmClient.generatePlanningResponse(planningPrompt); //fallback plan is probably not valid*
        log.debug("Planning response:\n{}", planningResponse);

        // 3. Parsing avec retry spécifique Mistral
        ExecutionPlan plan = null;
        try {
            plan = responseParser.parseWithMistralRetry(planningResponse, 3);
        } catch (ResponseParsingException e) {
            log.error("Error parsing planning response", e);
            throw new RuntimeException(e);
        }

        // 4. Exécution des actions
        Map<String, ActionResult> results = actionExecutor.executeActions(plan);    //TODO
        log.debug("Action execution results: {}", results);

        // 5. Prompt de formulation
        String formulationPrompt = promptBuilder.buildFormulationPrompt(
                userQuery, plan, results, context
        );
        log.debug("Formulation prompt:\n{}", formulationPrompt);

        // 6. Appel à Mistral pour formulation
        String finalResponse = llmClient.generateFormulationResponse(formulationPrompt);
        log.info("Final response: {}", finalResponse);

        // 7. Ajout à la mémoire à court terme.
        context.addUserMessage(userQuery);
        context.addAssistantMessage(finalResponse, plan);
        triggerPersonalityProcessing(lastInteracted);
        lastInteracted = LocalDateTime.now();
        return finalResponse;
    }


    private void triggerPersonalityProcessing(LocalDateTime lastInteraction) {
        CompletableFuture.runAsync(() -> {
            try {
                // On ne veut pas que la personnalité se déclenche à chaque interaction.
                Duration elapsedTime = Duration.between(lastInteraction, LocalDateTime.now());

                if (context.getMessageHistory().size() > 2 && elapsedTime.toMinutes() > 10) {
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
