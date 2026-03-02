package org.arcos.PlannedAction.Models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class ExecutionHistoryEntry {

    @JsonProperty("id")
    private String id;

    @JsonProperty("actionId")
    private String actionId;

    @JsonProperty("label")
    private String label;

    @JsonProperty("executedAt")
    private LocalDateTime executedAt;

    @JsonProperty("result")
    private String result;

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("context")
    private String context;

    public ExecutionHistoryEntry() {
        this.id = UUID.randomUUID().toString();
        this.executedAt = LocalDateTime.now();
    }

    public ExecutionHistoryEntry(String actionId, String label, String result, boolean success, String context) {
        this.id = UUID.randomUUID().toString();
        this.actionId = actionId;
        this.label = label;
        this.executedAt = LocalDateTime.now();
        this.result = result;
        this.success = success;
        this.context = context;
    }
}
