package org.arcos.LLM.Client.ResponseObject;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DesireResponse
{
    @JsonProperty("label")
    private String label;       // title of the desire

    @JsonProperty("description")
    private String description;

    @JsonProperty("intensity")
    private double intensity;    // 0..1

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
}
