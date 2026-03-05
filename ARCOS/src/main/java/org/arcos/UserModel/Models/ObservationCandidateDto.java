package org.arcos.UserModel.Models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ObservationCandidateDto {

    @JsonProperty("observation")
    private String observation;

    @JsonProperty("branche")
    private String branche;

    @JsonProperty("remplace")
    private String remplace;

    @JsonProperty("explicite")
    private boolean explicite;

    public ObservationCandidateDto() {
    }

    public ObservationCandidateDto(String observation, String branche, String remplace, boolean explicite) {
        this.observation = observation;
        this.branche = branche;
        this.remplace = remplace;
        this.explicite = explicite;
    }

    public String getObservation() {
        return observation;
    }

    public void setObservation(String observation) {
        this.observation = observation;
    }

    public String getBranche() {
        return branche;
    }

    public void setBranche(String branche) {
        this.branche = branche;
    }

    public String getRemplace() {
        return remplace;
    }

    public void setRemplace(String remplace) {
        this.remplace = remplace;
    }

    public boolean isExplicite() {
        return explicite;
    }

    public void setExplicite(boolean explicite) {
        this.explicite = explicite;
    }
}
