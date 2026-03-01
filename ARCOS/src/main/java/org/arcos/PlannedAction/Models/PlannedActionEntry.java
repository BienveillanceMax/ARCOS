package org.arcos.PlannedAction.Models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class PlannedActionEntry {

    @JsonProperty("id")
    private String id;

    @JsonProperty("label")
    private String label;

    @JsonProperty("actionType")
    private ActionType actionType;

    @JsonProperty("status")
    private ActionStatus status;

    @JsonProperty("triggerDatetime")
    private LocalDateTime triggerDatetime;

    @JsonProperty("cronExpression")
    private String cronExpression;

    @JsonProperty("executionPlan")
    private ReWOOPlan executionPlan;

    @JsonProperty("synthesisPromptTemplate")
    private String synthesisPromptTemplate;

    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    @JsonProperty("lastExecutedAt")
    private LocalDateTime lastExecutedAt;

    @JsonProperty("executionCount")
    private int executionCount;

    @JsonProperty("lastExecutionResult")
    private String lastExecutionResult;

    public PlannedActionEntry() {
        this.id = UUID.randomUUID().toString();
        this.status = ActionStatus.ACTIVE;
        this.createdAt = LocalDateTime.now();
        this.executionCount = 0;
    }

    @JsonIgnore
    public boolean isSimpleReminder() {
        return executionPlan == null || executionPlan.getSteps() == null || executionPlan.getSteps().isEmpty();
    }

    @JsonIgnore
    public boolean isHabit() {
        return actionType == ActionType.HABIT;
    }
}
