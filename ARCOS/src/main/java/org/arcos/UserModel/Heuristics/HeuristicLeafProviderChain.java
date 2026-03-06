package org.arcos.UserModel.Heuristics;

import lombok.extern.slf4j.Slf4j;
import org.arcos.UserModel.Models.ObservationLeaf;
import org.arcos.UserModel.Models.SignificantChange;
import org.springframework.context.annotation.Primary;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@Primary
public class HeuristicLeafProviderChain implements HeuristicLeafProvider {

    private final HeuristicTextTemplates templates;
    private final HeuristicNarrativeGenerator generator;

    public HeuristicLeafProviderChain(HeuristicTextTemplates templates,
                                      @Nullable HeuristicNarrativeGenerator generator) {
        this.templates = templates;
        this.generator = generator;
    }

    @Override
    public List<ObservationLeaf> generateLeaves(List<SignificantChange> changes, int conversationCount) {
        // Always use templates for cold-start (< 5 conversations)
        if (conversationCount < 5) {
            return templates.generateLeaves(changes, conversationCount);
        }

        if (generator != null && generator.isAvailable()) {
            try {
                List<ObservationLeaf> result = generator.generateLeaves(changes, conversationCount);
                if (result != null && !result.isEmpty()) {
                    return result;
                }
            } catch (Exception e) {
                log.warn("HeuristicNarrativeGenerator failed, falling back to templates: {}", e.getMessage());
            }
        }

        return templates.generateLeaves(changes, conversationCount);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
