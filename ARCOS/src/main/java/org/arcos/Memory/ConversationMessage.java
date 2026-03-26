package org.arcos.Memory;

import org.arcos.Orchestrator.Entities.ExecutionPlan;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Représente un message dans la conversation
 */
public class ConversationMessage {

    public enum MessageType {
        USER, ASSISTANT, SYSTEM
    }

    @JsonProperty("type")
    private MessageType type;

    @JsonProperty("content")
    private String content;

    @JsonProperty("timestamp")
    private LocalDateTime timestamp;

    @JsonProperty("execution_plan")
    private ExecutionPlan executionPlan;

    @JsonProperty("metadata")
    private Map<String, String> metadata;

    public ConversationMessage() {
        this.timestamp = LocalDateTime.now();
        this.metadata = new HashMap<>();
    }

    public ConversationMessage(MessageType type, String content, ExecutionPlan executionPlan) {
        this();
        this.type = type;
        this.content = content;
        this.executionPlan = executionPlan;
    }

    // Getters et Setters
    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public ExecutionPlan getExecutionPlan() { return executionPlan; }
    public void setExecutionPlan(ExecutionPlan executionPlan) { this.executionPlan = executionPlan; }

    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
}
