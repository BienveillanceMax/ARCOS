package Orchestrator.Entities;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class ExecutionPlan {

    @JsonProperty("reasoning")
    private String reasoning;

    @JsonProperty("actions")
    private List<PlannedAction> actions;

    // Constructeurs
    public ExecutionPlan() {}

    public ExecutionPlan(String reasoning, List<PlannedAction> actions) {
        this.reasoning = reasoning;
        this.actions = actions;
    }

    // Getters et Setters
    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }

    public List<PlannedAction> getActions() { return actions; }

    public void setActions(List<PlannedAction> actions) { this.actions = actions; }

    // Méthodes utilitaires
    public boolean isEmpty() {
        return actions == null || actions.isEmpty();
    }

    public int getActionCount() {
        return actions != null ? actions.size() : 0;
    }

    @Override
    public String toString() {
        return String.format("ExecutionPlan{reasoning='%s', actions=%d}",
                reasoning, getActionCount());
    }

    // Classe interne pour les actions planifiées
    public static class PlannedAction {

        @JsonProperty("name")
        private String name;

        @JsonProperty("parameters")
        private Map<String, Object> parameters;

        // Constructeurs
        public PlannedAction() {}

        public PlannedAction(String name, Map<String, Object> parameters) {
            this.name = name;
            this.parameters = parameters;
        }

        // Getters et Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }

        @Override
        public String toString() {
            return String.format("PlannedAction{name='%s', parameters=%s}", name, parameters);
        }
    }
}

