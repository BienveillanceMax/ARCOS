package org.arcos.LLM.Client.ResponseObject;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.arcos.PlannedAction.Models.ReWOOPlan;

@Data
public class PlannedActionPlanResponse {

    @JsonProperty("executionPlan")
    private ReWOOPlan executionPlan;

    @JsonProperty("synthesisPromptTemplate")
    private String synthesisPromptTemplate;
}
