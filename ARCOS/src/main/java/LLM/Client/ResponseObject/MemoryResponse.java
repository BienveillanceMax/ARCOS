package LLM.Client.ResponseObject;

import Memory.LongTermMemory.Models.Subject;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MemoryResponse
{
    @JsonProperty("content")
    private String content;

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
