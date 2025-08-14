package Orchestrator;

import Exceptions.PlanningException;
import LLM.LLMClient;
import Memory.ActionRegistry;
import Memory.ConversationContext;
import Orchestrator.Entities.ExecutionPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ActionPlanner {

    @Autowired
    private LLMClient llmClient;

    @Autowired
    private ActionRegistry actionRegistry;

    public ExecutionPlan planActions(String userQuery, ConversationContext context) {
        String prompt = buildPlanningPrompt(userQuery, context);
        String llmResponse = llmClient.generatePlanningResponse(prompt);
        return parsePlanFromLLMResponse(llmResponse);
    }

    private String buildPlanningPrompt(String query, ConversationContext context) {
        return String.format("""
            Tu es un planificateur d'actions. Voici les actions disponibles:
            %s
            
            Contexte de conversation: %s
            Requête utilisateur: %s
            
            Réponds UNIQUEMENT avec un JSON contenant le plan d'actions:
            {
              "actions": [
                {
                  "name": "nom_action",
                  "parameters": {"param1": "valeur1"},
                  "reasoning": "pourquoi cette action"
                }
              ]
            }
            """,
                actionRegistry.getActionsAsJson(),
                context.getSummary(),
                query
        );
    }

    private ExecutionPlan parsePlanFromLLMResponse(String response) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(response, ExecutionPlan.class);
        } catch (Exception e) {
            throw new PlanningException("Impossible de parser le plan: " + response, e);
        }
    }
}