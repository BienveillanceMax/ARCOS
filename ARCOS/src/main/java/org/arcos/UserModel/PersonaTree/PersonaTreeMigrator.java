package org.arcos.UserModel.PersonaTree;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.arcos.UserModel.UserModelProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Migrates old user-tree.json (TreeSnapshot format) to new persona-tree.json.
 * Maps old TreeBranch observations to PersonaTree leaf paths.
 * Uses raw JSON parsing to avoid dependency on deleted v1 model classes.
 */
@Slf4j
@Component
public class PersonaTreeMigrator {

    private final PersonaTreeService treeService;
    private final UserModelProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Branch mapping: old TreeBranch name → new PersonaTree leaf path
    private static final Map<String, String> BRANCH_TO_LEAF_PATH = Map.of(
            "IDENTITE",       "2_Psychological_Characteristics.Self_Perception.Identity.Clarity",
            "COMMUNICATION",  "5_Behavioral_Characteristics.Social_Interactions.Communication_Style.Tendencies",
            "HABITUDES",      "5_Behavioral_Characteristics.Behavioral_Habits.Daily_Routine",
            "OBJECTIFS",      "4_Identity_Characteristics.Motivations_and_Goals.Goals.Long_Term",
            "EMOTIONS",       "2_Psychological_Characteristics.Psychological_State.Emotional_Baseline",
            "INTERETS",       "5_Behavioral_Characteristics.Interests_and_Skills.Interests_and_Hobbies"
    );

    public PersonaTreeMigrator(PersonaTreeService treeService, UserModelProperties properties) {
        this.treeService = treeService;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        migrateIfNeeded();
    }

    public void migrateIfNeeded() {
        Path personaTreePath = Paths.get(properties.getPersonaTreePath());
        Path oldTreePath = Paths.get(properties.getStoragePath());

        if (Files.exists(personaTreePath)) {
            log.debug("PersonaTree already exists at {}, skipping migration", personaTreePath);
            return;
        }

        if (!Files.exists(oldTreePath)) {
            log.debug("Old user tree not found at {}, skipping migration", oldTreePath);
            return;
        }

        log.info("Migrating old user tree from {} to PersonaTree format", oldTreePath);

        try {
            JsonNode root = objectMapper.readTree(Files.readAllBytes(oldTreePath));
            JsonNode branchesNode = root.get("branches");

            int totalObservations = 0;
            int migratedBranches = 0;

            if (branchesNode != null && branchesNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = branchesNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    String branchName = entry.getKey();
                    JsonNode observations = entry.getValue();

                    if (!observations.isArray() || observations.isEmpty()) {
                        continue;
                    }

                    String targetPath = BRANCH_TO_LEAF_PATH.get(branchName);
                    if (targetPath == null) {
                        log.warn("No mapping for branch {}, skipping {} observations", branchName, observations.size());
                        continue;
                    }

                    LinkedHashSet<String> uniqueTexts = new LinkedHashSet<>();
                    for (JsonNode obs : observations) {
                        JsonNode textNode = obs.get("text");
                        if (textNode != null && !textNode.isNull() && !textNode.asText().isEmpty()) {
                            uniqueTexts.add(textNode.asText());
                        }
                    }

                    if (!uniqueTexts.isEmpty()) {
                        String joinedValue = String.join(" ; ", uniqueTexts);
                        treeService.setLeafValue(targetPath, joinedValue);
                        totalObservations += uniqueTexts.size();
                        migratedBranches++;
                        log.debug("Migrated {} observations from {} to {}", uniqueTexts.size(), branchName, targetPath);
                    }
                }
            }

            treeService.persist();
            log.info("Migration complete: {} observations from {} branches", totalObservations, migratedBranches);

        } catch (IOException e) {
            log.error("Failed to migrate old user tree from {}: {}", oldTreePath, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            log.error("Migration failed due to invalid PersonaTree path: {}", e.getMessage(), e);
        }
    }
}
