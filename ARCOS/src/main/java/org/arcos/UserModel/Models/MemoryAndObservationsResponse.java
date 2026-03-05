package org.arcos.UserModel.Models;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.arcos.LLM.Client.ResponseObject.MemoryResponse;

import java.util.List;

public class MemoryAndObservationsResponse extends MemoryResponse {

    @JsonProperty("userObservations")
    private List<ObservationCandidateDto> userObservations;

    public List<ObservationCandidateDto> getUserObservations() {
        return userObservations;
    }

    public void setUserObservations(List<ObservationCandidateDto> userObservations) {
        this.userObservations = userObservations;
    }
}
