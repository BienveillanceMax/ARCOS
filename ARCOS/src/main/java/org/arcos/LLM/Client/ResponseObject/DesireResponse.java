package org.arcos.LLM.Client.ResponseObject;

import org.arcos.Memory.LongTermMemory.Models.DesireEntry;

public class DesireResponse
{
    private String label;       // title of the desire
    private String description;
    private double intensity;    // 0..1
    private String reasoning;
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
