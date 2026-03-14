package org.arcos.UserModel.DfsNavigator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class BranchDescriptionRegistry {

    public record L1BranchEntry(String key, String description, String treePath) {}

    private List<L1BranchEntry> l1Branches = List.of();
    private Map<String, L2Group> l2GroupsByParent = Map.of();

    @PostConstruct
    public void initialize() {
        try {
            ClassPathResource resource = new ClassPathResource("branch-descriptions.json");
            try (InputStream is = resource.getInputStream()) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(is);

                // Parse L1 branches
                JsonNode l1Node = root.get("l1Branches");
                List<L1BranchEntry> entries = new ArrayList<>();
                for (JsonNode item : l1Node) {
                    entries.add(new L1BranchEntry(
                            item.get("key").asText(),
                            item.get("description").asText(),
                            item.get("treePath").asText()
                    ));
                }
                this.l1Branches = Collections.unmodifiableList(entries);

                // Parse L2 groups
                JsonNode l2Node = root.get("l2Groups");
                Map<String, L2Group> groups = new LinkedHashMap<>();
                for (JsonNode groupNode : l2Node) {
                    String parentKey = groupNode.get("parentKey").asText();
                    List<L2Branch> branches = new ArrayList<>();
                    for (JsonNode branchNode : groupNode.get("branches")) {
                        branches.add(new L2Branch(
                                branchNode.get("key").asText(),
                                branchNode.get("description").asText()
                        ));
                    }
                    groups.put(parentKey, new L2Group(parentKey, Collections.unmodifiableList(branches)));
                }
                this.l2GroupsByParent = Collections.unmodifiableMap(groups);

                log.info("BranchDescriptionRegistry loaded: {} L1 branches, {} L2 groups",
                        l1Branches.size(), l2GroupsByParent.size());
            }
        } catch (IOException e) {
            log.error("Failed to load branch-descriptions.json", e);
            throw new IllegalStateException("Cannot load branch-descriptions.json", e);
        }
    }

    public List<L1BranchEntry> getL1Branches() {
        return l1Branches;
    }

    public List<String> getL1Descriptions() {
        return l1Branches.stream()
                .map(L1BranchEntry::description)
                .collect(Collectors.toList());
    }

    public List<String> getL1Keys() {
        return l1Branches.stream()
                .map(L1BranchEntry::key)
                .collect(Collectors.toList());
    }

    public Optional<L2Group> getL2GroupForParent(String l1Key) {
        return Optional.ofNullable(l2GroupsByParent.get(l1Key));
    }

    public String getTreePathForL1(String l1Key) {
        return l1Branches.stream()
                .filter(e -> e.key().equals(l1Key))
                .findFirst()
                .map(L1BranchEntry::treePath)
                .orElseThrow(() -> new IllegalArgumentException("Unknown L1 key: " + l1Key));
    }

    public String getTreePathForL2(String l1Key, String l2Key) {
        return getTreePathForL1(l1Key) + "." + l2Key;
    }
}
