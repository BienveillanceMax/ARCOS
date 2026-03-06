package org.arcos.UserModel.Heuristics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.arcos.LLM.Local.LocalLlmService;
import org.arcos.UserModel.Models.ObservationLeaf;
import org.arcos.UserModel.Models.ObservationSource;
import org.arcos.UserModel.Models.SignificantChange;
import org.arcos.UserModel.UserModelProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@ConditionalOnProperty(name = "arcos.local-llm.enabled", havingValue = "true")
public class HeuristicNarrativeGenerator implements HeuristicLeafProvider {

    private final LocalLlmService localLlmService;
    private final UserModelProperties properties;

    public HeuristicNarrativeGenerator(LocalLlmService localLlmService,
                                       UserModelProperties properties) {
        this.localLlmService = localLlmService;
        this.properties = properties;
    }

    @Override
    public List<ObservationLeaf> generateLeaves(List<SignificantChange> changes, int conversationCount) {
        if (changes.isEmpty()) {
            return Collections.emptyList();
        }

        String prompt = buildNarrativePrompt(changes);

        try {
            String response = localLlmService.generateSimpleAsync(prompt)
                    .get(properties.getConsolidation().getTimeoutMs(), TimeUnit.MILLISECONDS);

            if (response == null || response.isBlank()) {
                return Collections.emptyList();
            }

            return parseNarrativeResponse(response, changes);
        } catch (Exception e) {
            log.warn("Failed to generate narrative leaves: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public boolean isAvailable() {
        return localLlmService.isAvailable();
    }

    String buildNarrativePrompt(List<SignificantChange> changes) {
        StringBuilder changesDesc = new StringBuilder();
        for (SignificantChange change : changes) {
            changesDesc.append("- Signal: %s, branche: %s, ancienne valeur: %.2f, nouvelle valeur: %.2f\n".formatted(
                    change.signalName(), change.branch(), change.oldValue(), change.newValue()));
        }

        return """
                Tu es un systeme de modelisation utilisateur.
                A partir des changements significatifs observes dans le comportement de l'utilisateur, genere des observations factuelles et descriptives.
                Chaque observation doit commencer par "Mon createur" et decrire un trait ou comportement.
                Reponds avec une observation par ligne, sans numerotation ni tiret.

                Changements observes:
                %s
                Observations:
                """.formatted(changesDesc);
    }

    private List<ObservationLeaf> parseNarrativeResponse(String response, List<SignificantChange> changes) {
        List<ObservationLeaf> leaves = new ArrayList<>();
        String[] lines = response.split("\n");

        for (String line : lines) {
            String trimmed = line.trim();
            // Remove common prefixes (-, *, numbers)
            trimmed = trimmed.replaceFirst("^[-*\\d.]+\\s*", "");
            if (trimmed.startsWith("Mon createur") || trimmed.startsWith("Mon créateur")) {
                // Associate with the first matching change's branch, or default to COMMUNICATION
                var branch = changes.isEmpty()
                        ? org.arcos.UserModel.Models.TreeBranch.COMMUNICATION
                        : changes.get(Math.min(leaves.size(), changes.size() - 1)).branch();
                leaves.add(new ObservationLeaf(trimmed, branch, ObservationSource.HEURISTIC));
            }
        }

        return leaves;
    }
}
