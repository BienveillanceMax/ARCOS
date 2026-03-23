package org.arcos.LLM.Client.ResponseObject;

import org.arcos.Memory.LongTermMemory.Models.Subject;
import com.fasterxml.jackson.annotation.JsonProperty;

public class MemoryResponse
{
    @JsonProperty("content")
    private String content;

    @JsonProperty("canonicalText")
    private String canonicalText;

    @JsonProperty("subject")
    private Subject subject;

    @JsonProperty("satisfaction")
    private double satisfaction;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCanonicalText() {
        return canonicalText;
    }

    public void setCanonicalText(String canonicalText) {
        this.canonicalText = canonicalText;
    }

    public Subject getSubject() {
        return subject;
    }

    public void setSubject(Subject subject) {
        this.subject = subject;
    }

    public double getSatisfaction() {
        return satisfaction;
    }

    public void setSatisfaction(double satisfaction) {
        this.satisfaction = satisfaction;
    }
}
