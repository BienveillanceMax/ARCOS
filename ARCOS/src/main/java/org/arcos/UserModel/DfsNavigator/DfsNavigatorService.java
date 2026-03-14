package org.arcos.UserModel.DfsNavigator;

import lombok.extern.slf4j.Slf4j;
import org.arcos.UserModel.PersonaTree.PersonaTreeGate;
import org.arcos.UserModel.UserModelProperties;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DfsNavigatorService {

    private final CrossEncoderService crossEncoder;
    private final BranchDescriptionRegistry registry;
    private final PersonaTreeGate personaTreeGate;
    private final int topNL1;
    private final float l2Threshold;

    public DfsNavigatorService(CrossEncoderService crossEncoder,
                               BranchDescriptionRegistry registry,
                               PersonaTreeGate personaTreeGate,
                               UserModelProperties properties) {
        this.crossEncoder = crossEncoder;
        this.registry = registry;
        this.personaTreeGate = personaTreeGate;
        this.topNL1 = properties.getDfsTopNL1();
        this.l2Threshold = properties.getDfsL2Threshold();
    }

    /**
     * Navigate the PersonaTree using DFS guided by the cross-encoder.
     * @param query user query to find relevant profile branches
     * @return DfsResult with relevant leaves, selected branches, and latency
     */
    public DfsResult navigate(String query) {
        long start = System.currentTimeMillis();

        if (!crossEncoder.isAvailable()) {
            log.debug("CrossEncoder not available, returning empty DFS result");
            return new DfsResult(Map.of(), List.of(), List.of(), System.currentTimeMillis() - start);
        }

        // 1. Score L1 branches
        List<String> l1Descriptions = registry.getL1Descriptions();
        float[] l1Scores = crossEncoder.score(query, l1Descriptions);

        if (l1Scores.length == 0) {
            return new DfsResult(Map.of(), List.of(), List.of(), System.currentTimeMillis() - start);
        }

        List<BranchDescriptionRegistry.L1BranchEntry> l1Entries = registry.getL1Branches();
        List<BranchScore> scoredL1 = new ArrayList<>();
        for (int i = 0; i < l1Entries.size(); i++) {
            scoredL1.add(new BranchScore(l1Entries.get(i).key(), l1Entries.get(i).description(), l1Scores[i]));
        }
        Collections.sort(scoredL1);

        List<BranchScore> topL1 = scoredL1.subList(0, Math.min(topNL1, scoredL1.size()));
        List<String> selectedL1Keys = topL1.stream().map(BranchScore::key).collect(Collectors.toList());

        log.debug("DFS L1 top-{}: {}", topNL1, selectedL1Keys);

        // 2. For each retained L1, drill into L2
        Map<String, String> allLeaves = new LinkedHashMap<>();
        List<String> selectedL2Keys = new ArrayList<>();

        for (BranchScore l1 : topL1) {
            Optional<L2Group> l2Group = registry.getL2GroupForParent(l1.key());

            if (l2Group.isPresent()) {
                // Score L2 branches
                List<L2Branch> l2Branches = l2Group.get().branches();
                List<String> l2Descriptions = l2Branches.stream()
                        .map(L2Branch::description)
                        .collect(Collectors.toList());

                float[] l2Scores = crossEncoder.score(query, l2Descriptions);
                if (l2Scores.length == 0) continue;

                for (int i = 0; i < l2Branches.size(); i++) {
                    if (l2Scores[i] >= l2Threshold) {
                        String l2Key = l2Branches.get(i).key();
                        selectedL2Keys.add(l2Key);
                        String treePath = registry.getTreePathForL2(l1.key(), l2Key);
                        Map<String, String> leaves = personaTreeGate.getLeavesUnderPath(treePath);
                        allLeaves.putAll(leaves);
                    }
                }
            } else {
                // Terminal L1 (no L2 group): collect leaves directly
                String treePath = registry.getTreePathForL1(l1.key());
                Map<String, String> leaves = personaTreeGate.getLeavesUnderPath(treePath);
                allLeaves.putAll(leaves);
            }
        }

        long latency = System.currentTimeMillis() - start;
        log.debug("DFS navigation completed in {}ms: {} leaves from {} L1 + {} L2 branches",
                latency, allLeaves.size(), selectedL1Keys.size(), selectedL2Keys.size());

        return new DfsResult(allLeaves, selectedL1Keys, selectedL2Keys, latency);
    }
}
