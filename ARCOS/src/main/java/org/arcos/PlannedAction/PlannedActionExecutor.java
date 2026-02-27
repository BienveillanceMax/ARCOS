package org.arcos.PlannedAction;

import lombok.extern.slf4j.Slf4j;
import org.arcos.LLM.Client.LLMClient;
import org.arcos.LLM.Prompts.PromptBuilder;
import org.arcos.PlannedAction.Models.PlannedActionEntry;
import org.arcos.PlannedAction.Models.ReWOOPlan;
import org.arcos.Tools.Actions.ActionResult;
import org.arcos.Tools.Actions.CalendarActions;
import org.arcos.Tools.Actions.PythonActions;
import org.arcos.Tools.Actions.SearchActions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class PlannedActionExecutor {

    private final LLMClient llmClient;
    private final PromptBuilder promptBuilder;
    private final Map<String, Function<Map<String, Object>, ActionResult>> toolRegistry;

    public PlannedActionExecutor(CalendarActions calendarActions,
                                  SearchActions searchActions,
                                  PythonActions pythonActions,
                                  LLMClient llmClient,
                                  PromptBuilder promptBuilder) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.toolRegistry = new HashMap<>();

        toolRegistry.put("Chercher_sur_Internet", params -> {
            String query = (String) params.get("query");
            return searchActions.searchTheWeb(query);
        });

        toolRegistry.put("Lister_les_evenements_a_venir", params -> {
            int maxResults = params.containsKey("maxResults")
                    ? ((Number) params.get("maxResults")).intValue()
                    : 5;
            return calendarActions.listCalendarEvents(maxResults);
        });

        toolRegistry.put("Ajouter_un_evenement_au_calendrier", params -> {
            String title = (String) params.get("title");
            String description = (String) params.get("description");
            String location = (String) params.get("location");
            String startDateTimeStr = (String) params.get("startDateTimeStr");
            String endDateTimeStr = (String) params.get("endDateTimeStr");
            return calendarActions.AddCalendarEvent(title, description, location, startDateTimeStr, endDateTimeStr);
        });

        toolRegistry.put("Supprimer_un_evenement", params -> {
            String title = (String) params.get("title");
            return calendarActions.deleteCalendarEvent(title);
        });

        toolRegistry.put("Python_Execution", params -> {
            String code = (String) params.get("code");
            return pythonActions.executePythonCode(code);
        });
    }

    public String execute(PlannedActionEntry action) {
        if (action.isSimpleReminder()) {
            return "Rappel : " + action.getLabel();
        }

        ReWOOPlan plan = action.getExecutionPlan();
        Map<String, ActionResult> stepResults = new LinkedHashMap<>();

        for (ReWOOPlan.ReWOOStep step : plan.getSteps()) {
            log.info("Executing step {} : {} (tool: {})", step.getStepId(), step.getDescription(), step.getToolName());

            Function<Map<String, Object>, ActionResult> tool = toolRegistry.get(step.getToolName());
            if (tool == null) {
                log.warn("Unknown tool: {}", step.getToolName());
                stepResults.put(step.getOutputVariable(), ActionResult.failure("Outil inconnu : " + step.getToolName()));
                continue;
            }

            try {
                Map<String, Object> resolvedParams = resolveParameters(step.getParameters(), stepResults);
                ActionResult result = tool.apply(resolvedParams);
                stepResults.put(step.getOutputVariable(), result);
                log.info("Step {} completed: success={}", step.getStepId(), result.isSuccess());
            } catch (Exception e) {
                log.warn("Step {} failed: {}", step.getStepId(), e.getMessage());
                stepResults.put(step.getOutputVariable(), ActionResult.failure("Erreur à l'étape " + step.getStepId(), e));
            }
        }

        if (action.getSynthesisPromptTemplate() != null && !action.getSynthesisPromptTemplate().isBlank()) {
            try {
                Prompt synthesisPrompt = promptBuilder.buildPlannedActionSynthesisPrompt(action, stepResults);
                String synthesis = llmClient.generateToollessResponse(synthesisPrompt);
                if (synthesis != null && !synthesis.isBlank()) {
                    return synthesis;
                }
            } catch (Exception e) {
                log.warn("Synthesis LLM call failed, falling back to raw concatenation", e);
            }
        }

        return buildFallbackResult(stepResults);
    }

    Map<String, Object> resolveParameters(Map<String, Object> params, Map<String, ActionResult> stepResults) {
        Map<String, Object> resolved = new HashMap<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String strValue && strValue.startsWith("$")) {
                String varName = strValue.substring(1);
                ActionResult previousResult = stepResults.get(varName);
                if (previousResult != null) {
                    resolved.put(entry.getKey(), previousResult.getMessage());
                } else {
                    log.warn("Unresolved variable reference: {}", strValue);
                    resolved.put(entry.getKey(), strValue);
                }
            } else {
                resolved.put(entry.getKey(), value);
            }
        }
        return resolved;
    }

    private String buildFallbackResult(Map<String, ActionResult> stepResults) {
        return stepResults.entrySet().stream()
                .map(entry -> entry.getKey() + " : " + entry.getValue().getMessage())
                .collect(Collectors.joining(". "));
    }
}
