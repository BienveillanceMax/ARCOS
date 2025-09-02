package Orchestrator;

import EventBus.EventQueue;
import EventBus.Events.Event;
import EventBus.Events.EventType;
import Exceptions.ResponseParsingException;
import IO.OuputHandling.TTSHandler;
import LLM.LLMResponseParser;
import LLM.LLMClient;
import Memory.ConversationContext;
import Memory.Actions.Entities.ActionResult;
import Orchestrator.Entities.ExecutionPlan;
import LLM.Prompts.PromptBuilder;
import Personality.PersonalityOrchestrator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Component
public class Orchestrator
{
    private final EventQueue eventQueue;
    private final LLMClient llmClient;
    private final PromptBuilder promptBuilder;
    private final LLMResponseParser responseParser;
    private final ActionExecutor actionExecutor;
    private final ConversationContext context;
    private final PersonalityOrchestrator personalityOrchestrator;

    @Autowired
    public Orchestrator(EventQueue evenQueue, LLMClient llmClient, PromptBuilder promptBuilder, LLMResponseParser responseParser, ActionExecutor actionExecutor, ConversationContext context, PersonalityOrchestrator personalityOrchestrator) {
        this.eventQueue = evenQueue;
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.responseParser = responseParser;
        this.actionExecutor = actionExecutor;
        this.context = context;
        this.personalityOrchestrator = personalityOrchestrator;
    }


    private void dispatch(Event<?> event)
    {
        if (event.getType() == EventType.WAKEWORD)
        {

            //todo change
            processQuery((String)event.getPayload());
            TTSHandler ttsHandler = new TTSHandler();
            ttsHandler.initialize();
            System.out.println("starting processing");
            ttsHandler.speak( processQuery((String)event.getPayload()));
        }
    }

    public void start() {
        while (true) {
            try {
               Event<?> event = eventQueue.take();
               dispatch(event);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private String processQuery(String userQuery) {

        LocalDateTime lastInteraction = context.getLastUpdated();
        if (lastInteraction != null && Duration.between(lastInteraction, LocalDateTime.now()).toMinutes() > 15) {
            String conversation = context.getFullConversation();
            if (conversation != null && !conversation.isEmpty()) {
                personalityOrchestrator.processMemory(conversation);
            }
        }
        // 1. Génération du prompt
        String planningPrompt = promptBuilder.buildPlanningPrompt(userQuery, context);
        System.out.println(planningPrompt);
        System.out.println();

        // 2. Appel à Mistral pour planification
        String planningResponse = llmClient.generatePlanningResponse(planningPrompt); //fallback plan is probably not valid*
        System.out.println(planningResponse);
        System.out.println();

        // 3. Parsing avec retry spécifique Mistral
        ExecutionPlan plan = null;
        try {
            plan = responseParser.parseWithMistralRetry(planningResponse, 3);
        } catch (ResponseParsingException e) {
            throw new RuntimeException(e);
        }

        // 4. Exécution des actions
        Map<String, ActionResult> results = actionExecutor.executeActions(plan);    //TODO

        // 5. Prompt de formulation
        String formulationPrompt = promptBuilder.buildFormulationPrompt(
                userQuery, plan, results, context
        );

        // 6. Appel à Mistral pour formulation
        String finalResponse = llmClient.generateFormulationResponse(formulationPrompt);

        // 7. Ajout à la mémoire à court terme.
        context.addUserMessage(userQuery);
        context.addAssistantMessage(finalResponse,plan);

        return finalResponse;
    }
}
