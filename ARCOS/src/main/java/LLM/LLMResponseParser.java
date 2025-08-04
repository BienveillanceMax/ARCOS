package LLM;

import Exceptions.ResponseParsingException;
import Memory.ActionRegistry;
import Memory.Entities.Actions.Action;
import Orchestrator.Entities.ExecutionPlan;
import Orchestrator.Entities.Parameter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class LLMResponseParser {

    private final ObjectMapper objectMapper;
    private final ActionRegistry actionRegistry;


    public LLMResponseParser(ActionRegistry actionRegistry) {
        this.actionRegistry = actionRegistry;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.objectMapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
    }

    /**
     * Parse la réponse du LLM en ExecutionPlan
     */
    public ExecutionPlan parseExecutionPlan(String llmResponse) throws ResponseParsingException {
        try {
            // Nettoie la réponse
            String cleanJson = cleanLLMResponse(llmResponse);

            // Parse le JSON
            ExecutionPlan plan = objectMapper.readValue(cleanJson, ExecutionPlan.class);

            // Valide le plan
            validateExecutionPlan(plan);

            return plan;

        } catch (JsonProcessingException e) {
            throw new ResponseParsingException("JSON invalide dans la réponse du LLM: " + llmResponse, e);
        } catch (Exception e) {
            throw new ResponseParsingException("Erreur lors du parsing: " + e.getMessage(), e);
        }
    }

    /**
     * Parse la réponse du LLM avec retry automatique
     */
    public ExecutionPlan parseExecutionPlanWithRetry(String llmResponse, int maxRetries) throws ResponseParsingException {
        ResponseParsingException lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return parseExecutionPlan(llmResponse);
            } catch (ResponseParsingException e) {
                lastException = e;
                if (attempt < maxRetries) {
                    // Log de l'erreur pour debugging
                    System.err.println("Tentative " + attempt + " échouée: " + e.getMessage());
                }
            }
        }

        throw new ResponseParsingException("Échec du parsing après " + maxRetries + " tentatives", lastException);
    }

    /**
     * Nettoie la réponse du LLM pour extraire le JSON
     */
    private String cleanLLMResponse(String response) throws ResponseParsingException {
        if (response == null || response.trim().isEmpty()) {
            throw new ResponseParsingException("Réponse vide du LLM");
        }

        // Retire les blocs markdown
        response = response.replaceAll("```json\\s*", "").replaceAll("```", "");

        // Retire les espaces en début/fin
        response = response.trim();

        // Extrait le JSON si du texte l'entoure
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');

        if (start == -1 || end == -1 || end <= start) {
            throw new ResponseParsingException("Aucun JSON valide trouvé dans la réponse");
        }

        return response.substring(start, end + 1);
    }

    /**
     * Valide l'ExecutionPlan parsé
     */
    private void validateExecutionPlan(ExecutionPlan plan) throws ResponseParsingException {
        if (plan == null) {
            throw new ResponseParsingException("Plan d'exécution null");
        }

        if (plan.getReasoning() == null || plan.getReasoning().trim().isEmpty()) {
            throw new ResponseParsingException("Raisonnement manquant dans le plan");
        }

        if (plan.getActions() == null || plan.getActions().isEmpty()) {
            throw new ResponseParsingException("Aucune action planifiée");
        }

        // Valide chaque action
        for (int i = 0; i < plan.getActions().size(); i++) {
            ExecutionPlan.PlannedAction action = plan.getActions().get(i);
            validatePlannedAction(action, i);
        }
    }

    /**
     * Valide une action planifiée
     */
    private void validatePlannedAction(ExecutionPlan.PlannedAction action, int index) throws ResponseParsingException {
        if (action == null) {
            throw new ResponseParsingException("Action null à l'index " + index);
        }

        if (action.getName() == null || action.getName().trim().isEmpty()) {
            throw new ResponseParsingException("Nom d'action manquant à l'index " + index);
        }

        // Vérifie que l'action existe dans le registry
        if (!actionRegistry.hasAction(action.getName())) {
            throw new ResponseParsingException("Action inconnue: " + action.getName() + " à l'index " + index);
        }

        // Valide les paramètres par rapport à la définition de l'action
        Action actionDefinition = actionRegistry.getAction(action.getName());
        validateActionParameters(action, actionDefinition, index);
    }

    /**
     * Valide les paramètres d'une action
     */
    private void validateActionParameters(ExecutionPlan.PlannedAction plannedAction,
                                          Action actionDefinition, int index) throws ResponseParsingException {

        Map<String, Object> providedParams = plannedAction.getParameters();
        if (providedParams == null) {
            providedParams = Map.of(); // Map vide si null
        }

        // Vérifie les paramètres requis
        for (Parameter param : actionDefinition.getParameters()) {
            if (param.isRequired() &&
                    !providedParams.containsKey(param.getName()) &&
                    param.getDefaultValue() == null) {

                throw new ResponseParsingException(
                        String.format("Paramètre requis manquant '%s' pour l'action '%s' à l'index %d",
                                param.getName(), plannedAction.getName(), index));
            }
        }

        // Vérifie les types des paramètres fournis
        for (Map.Entry<String, Object> entry : providedParams.entrySet()) {
            String paramName = entry.getKey();
            Object paramValue = entry.getValue();

            Parameter paramDef = findParameterDefinition(actionDefinition, paramName);
            if (paramDef != null && paramValue != null) {
                validateParameterType(paramValue, paramDef, plannedAction.getName(), index);
            }
        }
    }

    /**
     * Trouve la définition d'un paramètre
     */
    private Parameter findParameterDefinition(Action action, String paramName) {
        return action.getParameters().stream()
                .filter(p -> p.getName().equals(paramName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Valide le type d'un paramètre
     */
    private void validateParameterType(Object value, Parameter paramDef, String actionName, int index)
            throws ResponseParsingException {

        Class<?> expectedType = paramDef.getType();
        Class<?> actualType = value.getClass();

        // Vérifications de type basiques
        if (expectedType == String.class && !(value instanceof String)) {
            throw new ResponseParsingException(
                    String.format("Type incorrect pour '%s' dans '%s' à l'index %d: attendu String, reçu %s",
                            paramDef.getName(), actionName, index, actualType.getSimpleName()));
        }

        if (expectedType == Integer.class && !(value instanceof Integer || value instanceof Number)) {
            throw new ResponseParsingException(
                    String.format("Type incorrect pour '%s' dans '%s' à l'index %d: attendu Integer, reçu %s",
                            paramDef.getName(), actionName, index, actualType.getSimpleName()));
        }

        if (expectedType == Boolean.class && !(value instanceof Boolean)) {
            throw new ResponseParsingException(
                    String.format("Type incorrect pour '%s' dans '%s' à l'index %d: attendu Boolean, reçu %s",
                            paramDef.getName(), actionName, index, actualType.getSimpleName()));
        }
    }


    public ExecutionPlan parseWithMistralRetry(String llmResponse, int maxRetries) throws ResponseParsingException {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return parseExecutionPlan(llmResponse);
            } catch (ResponseParsingException e) {
                if (attempt == maxRetries) {
                    // Fallback spécial pour Mistral
                    return createFallbackPlan(llmResponse);
                }

                // Log l'erreur et retry avec prompt modifié
                System.err.println("Mistral parsing failed, attempt " + attempt + ": " + e.getMessage());
            }
        }

        throw new ResponseParsingException("Failed after " + maxRetries + " attempts");
    }

    private ExecutionPlan createFallbackPlan(String originalResponse) {
        // Plan de secours si Mistral ne respecte pas le format JSON
        ExecutionPlan fallback = new ExecutionPlan();
        fallback.setReasoning("Réponse directe - format JSON non respecté");
        fallback.setActions(List.of(
                new ExecutionPlan.PlannedAction("respond_directly",
                        Map.of("content", originalResponse))
        ));
        return fallback;
    }


}
