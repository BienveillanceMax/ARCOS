package org.arcos.UserModel.Consolidation.Models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ConsolidationDecision(
        @JsonProperty("decision") ConsolidationAction decision,
        @JsonProperty("winner_id") String winnerId,
        @JsonProperty("merge_target_id") String mergeTargetId,
        @JsonProperty("new_text") String newText,
        @JsonProperty("target_branch") String targetBranch,
        @JsonProperty("confidence") double confidence,
        @JsonProperty("reasoning") String reasoning
) {
}
