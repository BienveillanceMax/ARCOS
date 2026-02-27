package org.arcos.PlannedAction.Models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ReWOOPlan {

    @JsonProperty("steps")
    private List<ReWOOStep> steps;

    public ReWOOPlan() {}

    public ReWOOPlan(List<ReWOOStep> steps) {
        this.steps = steps;
    }

    @Data
    public static class ReWOOStep {

        @JsonProperty("stepId")
        private int stepId;

        @JsonProperty("toolName")
        private String toolName;

        @JsonProperty("parameters")
        private Map<String, Object> parameters;

        @JsonProperty("outputVariable")
        private String outputVariable;

        @JsonProperty("description")
        private String description;

        public ReWOOStep() {}

        public ReWOOStep(int stepId, String toolName, Map<String, Object> parameters, String outputVariable, String description) {
            this.stepId = stepId;
            this.toolName = toolName;
            this.parameters = parameters;
            this.outputVariable = outputVariable;
            this.description = description;
        }
    }
}
