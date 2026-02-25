package org.arcos.LLM.Client.ResponseObject;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.arcos.Memory.LongTermMemory.Models.DesireEntry;

public class DesireResponse
{
    @JsonProperty("label")
    private String label;       // title of the desire

    @JsonProperty("description")
    private String description;

    @JsonProperty("intensity")
    private double intensity;    // 0..1

    @JsonProperty("reasoning")
    private String reasoning;

    @JsonProperty("status")
    private DesireEntry.Status status;


    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getIntensity() {
        return intensity;
    }

    public void setIntensity(double intensity) {
        this.intensity = intensity;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public DesireEntry.Status getStatus() {
        return status;
    }

    public void setStatus(DesireEntry.Status status) {
        this.status = status;
    }
}
