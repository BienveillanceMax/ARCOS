package Orchestrator;

import Exceptions.ResponseParsingException;
import LLM.LLMResponseParser;
import LLM.LLMService;
import Memory.ActionRegistry;
import Memory.ConversationContext;
import Memory.Entities.ActionResult;
import Memory.Entities.Actions.RespondAction;
import Orchestrator.Entities.ExecutionPlan;
import Prompts.PromptBuilder;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class Orchestrator
{
    private final LLMService llmService;
    private final PromptBuilder promptBuilder;
    private final LLMResponseParser responseParser;
    private final ActionExecutor actionExecutor;
    private final ConversationContext context;

    @Autowired
    public Orchestrator(LLMService llmService, PromptBuilder promptBuilder, LLMResponseParser responseParser, ActionExecutor actionExecutor, ConversationContext context) {
        this.llmService = llmService;
        this.promptBuilder = promptBuilder;
        this.responseParser = responseParser;
        this.actionExecutor = actionExecutor;
        this.context = context;
    }

    public String processQuery(String userQuery) {

        // 1. Génération du prompt
        String planningPrompt = promptBuilder.buildPlanningPrompt(userQuery, context);
        System.out.println(planningPrompt);
        System.out.println();

        // 2. Appel à Mistral pour planification
        String planningResponse = llmService.generatePlanningResponse(planningPrompt); //fallback plan is probably not valid*
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
        String finalResponse = llmService.generateFormulationResponse(formulationPrompt);

        return finalResponse;
    }
}
