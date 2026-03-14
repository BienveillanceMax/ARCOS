package org.arcos.UserModel.PersonaTree;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.arcos.UserModel.Engagement.EngagementRecord;
import org.arcos.UserModel.Models.ObservationLeaf;
import org.arcos.UserModel.Models.TreeBranch;
import org.arcos.UserModel.UserModelProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Migrates old user-tree.json (TreeSnapshot format) to new persona-tree.json.
 * Maps old TreeBranch observations to PersonaTree leaf paths.
 */
@Slf4j
@Component
public class PersonaTreeMigrator {

    private final PersonaTreeService treeService;
    private final UserModelProperties properties;
    private final ObjectMapper objectMapper;

    // Branch mapping: old TreeBranch → new PersonaTree leaf path
    private static final Map<TreeBranch, String> BRANCH_TO_LEAF_PATH = Map.of(
            TreeBranch.IDENTITE,       "2_Psychological_Characteristics.Self_Perception.Identity.Clarity",
            TreeBranch.COMMUNICATION,  "5_Behavioral_Characteristics.Social_Interactions.Communication_Style.Tendencies",
            TreeBranch.HABITUDES,      "5_Behavioral_Characteristics.Behavioral_Habits.Daily_Routine",
            TreeBranch.OBJECTIFS,      "4_Identity_Characteristics.Motivations_and_Goals.Goals.Long_Term",
            TreeBranch.EMOTIONS,       "2_Psychological_Characteristics.Psychological_State.Emotional_Baseline",
            TreeBranch.INTERETS,       "5_Behavioral_Characteristics.Interests_and_Skills.Interests_and_Hobbies"
    );

    public PersonaTreeMigrator(PersonaTreeService treeService, UserModelProperties properties) {
        this.treeService = treeService;
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        // Add mixin to ignore embedding field during deserialization
        this.objectMapper.addMixIn(ObservationLeaf.class, EmbeddingIgnoreMixin.class);
    }

    @PostConstruct
    public void init() {
        migrateIfNeeded();
    }

    /**
     * Migrate old tree to PersonaTree if needed.
     * Skips if persona-tree.json already exists or if old tree doesn't exist.
     */
    public void migrateIfNeeded() {
        Path personaTreePath = Paths.get(properties.getPersonaTreePath());
        Path oldTreePath = Paths.get(properties.getStoragePath());

        // Skip if persona-tree.json already exists (already migrated)
        if (Files.exists(personaTreePath)) {
            log.debug("PersonaTree already exists at {}, skipping migration", personaTreePath);
            return;
        }

        // Skip if old tree doesn't exist (nothing to migrate)
        if (!Files.exists(oldTreePath)) {
            log.debug("Old user tree not found at {}, skipping migration", oldTreePath);
            return;
        }

        log.info("Migrating old user tree from {} to PersonaTree format", oldTreePath);

        try {
            // Load old tree snapshot
            byte[] content = Files.readAllBytes(oldTreePath);
            TreeSnapshot snapshot = objectMapper.readValue(content, TreeSnapshot.class);

            int totalObservations = 0;
            int migratedBranches = 0;
            int unmappedBranches = 0;

            Map<TreeBranch, List<ObservationLeaf>> branches = snapshot.getBranches();
            if (branches != null) {
                for (Map.Entry<TreeBranch, List<ObservationLeaf>> entry : branches.entrySet()) {
                    TreeBranch branch = entry.getKey();
                    List<ObservationLeaf> observations = entry.getValue();

                    if (observations == null || observations.isEmpty()) {
                        continue;
                    }

                    String targetPath = BRANCH_TO_LEAF_PATH.get(branch);
                    if (targetPath == null) {
                        log.warn("No mapping found for branch {}, skipping {} observations", branch, observations.size());
                        unmappedBranches++;
                        continue;
                    }

                    // Deduplicate observations by text content
                    Set<String> uniqueTexts = observations.stream()
                            .map(ObservationLeaf::getText)
                            .filter(text -> text != null && !text.isEmpty())
                            .collect(Collectors.toCollection(LinkedHashSet::new));

                    if (!uniqueTexts.isEmpty()) {
                        // Join with " ; " separator
                        String joinedValue = String.join(" ; ", uniqueTexts);
                        treeService.setLeafValue(targetPath, joinedValue);
                        totalObservations += uniqueTexts.size();
                        migratedBranches++;
                        log.debug("Migrated {} unique observations from {} to {}",
                                uniqueTexts.size(), branch, targetPath);
                    }
                }
            }

            // Persist the migrated tree
            treeService.persist();

            log.info("Migration complete: {} unique observations migrated from {} branches, {} unmapped branches",
                    totalObservations, migratedBranches, unmappedBranches);

        } catch (IOException e) {
            log.error("Failed to migrate old user tree from {}: {}", oldTreePath, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            log.error("Migration failed due to invalid PersonaTree path: {}", e.getMessage(), e);
        }
    }

    /**
     * TreeSnapshot structure from old UserTreePersistenceService.
     * Only includes fields needed for migration.
     */
    @Data
    private static class TreeSnapshot {
        private Map<TreeBranch, List<ObservationLeaf>> branches;
        private int conversationCount;
        private Map<TreeBranch, String> summaries;
        private Map<String, Double> heuristicBaselines;
        private List<EngagementRecord> engagementHistory = new ArrayList<>();
        private Map<TreeBranch, Integer> lastGapQuestionPerBranch = new EnumMap<>(TreeBranch.class);
    }

    /**
     * Jackson mixin to exclude the embedding field from deserialization.
     */
    abstract static class EmbeddingIgnoreMixin {
        @JsonIgnore
        abstract float[] getEmbedding();
    }
}
