package org.arcos.UnitTests.UserModel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.arcos.UserModel.Models.ObservationLeaf;
import org.arcos.UserModel.Models.ObservationSource;
import org.arcos.UserModel.Models.TreeBranch;
import org.arcos.UserModel.PersonaTree.*;
import org.arcos.UserModel.UserModelProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PersonaTreeMigrator.
 * Uses real PersonaTreeService/Repository/SchemaLoader with @TempDir.
 */
class PersonaTreeMigratorTest {

    @TempDir
    Path tempDir;

    private PersonaTreeMigrator migrator;
    private PersonaTreeService treeService;
    private UserModelProperties properties;
    private ObjectMapper objectMapper;

    // Target leaf paths from the branch mapping
    private static final String PATH_IDENTITE = "2_Psychological_Characteristics.Self_Perception.Identity.Clarity";
    private static final String PATH_COMMUNICATION = "5_Behavioral_Characteristics.Social_Interactions.Communication_Style.Tendencies";
    private static final String PATH_HABITUDES = "5_Behavioral_Characteristics.Behavioral_Habits.Daily_Routine";
    private static final String PATH_OBJECTIFS = "4_Identity_Characteristics.Motivations_and_Goals.Goals.Long_Term";
    private static final String PATH_EMOTIONS = "2_Psychological_Characteristics.Psychological_State.Emotional_Baseline";
    private static final String PATH_INTERETS = "5_Behavioral_Characteristics.Interests_and_Skills.Interests_and_Hobbies";

    @BeforeEach
    void setUp() {
        // Create properties pointing to temp dir
        properties = new UserModelProperties();
        properties.setPersonaTreeSchemaPath("persona-tree-schema.json"); // from classpath
        properties.setStoragePath(tempDir.resolve("user-tree.json").toString());
        properties.setPersonaTreePath(tempDir.resolve("persona-tree.json").toString());

        // Create real PersonaTree stack (no mocks)
        PersonaTreeSchemaLoader schemaLoader = new PersonaTreeSchemaLoader(properties);
        schemaLoader.init();
        PersonaTreeRepository repository = new PersonaTreeRepository();
        treeService = new PersonaTreeService(schemaLoader, repository, properties);
        treeService.initialize();

        // Create migrator
        migrator = new PersonaTreeMigrator(treeService, properties);

        // ObjectMapper for writing old tree files
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    void migratesOldTreeToPersonaTree() throws IOException {
        // Given: old tree with COMMUNICATION and EMOTIONS observations
        TreeSnapshot oldTree = new TreeSnapshot();
        oldTree.setBranches(new EnumMap<>(TreeBranch.class));
        oldTree.getBranches().put(TreeBranch.COMMUNICATION, List.of(
                new ObservationLeaf("parle vite", TreeBranch.COMMUNICATION, ObservationSource.LLM_EXTRACTED),
                new ObservationLeaf("aime les blagues", TreeBranch.COMMUNICATION, ObservationSource.LLM_EXTRACTED)
        ));
        oldTree.getBranches().put(TreeBranch.EMOTIONS, List.of(
                new ObservationLeaf("calme en général", TreeBranch.EMOTIONS, ObservationSource.LLM_EXTRACTED)
        ));

        writeOldTree(oldTree);

        // When: migrate
        migrator.migrateIfNeeded();

        // Then: PersonaTree should have the values
        String commValue = treeService.getLeafValue(PATH_COMMUNICATION);
        assertNotNull(commValue);
        assertTrue(commValue.contains("parle vite"), "Should contain first observation");
        assertTrue(commValue.contains("aime les blagues"), "Should contain second observation");
        assertTrue(commValue.contains(" ; "), "Should use ';' separator");

        String emoValue = treeService.getLeafValue(PATH_EMOTIONS);
        assertEquals("calme en général", emoValue, "Single observation should not have separator");

        // Persona tree file should exist
        assertTrue(Files.exists(Path.of(properties.getPersonaTreePath())), "Persona tree file should be persisted");
    }

    @Test
    void deduplicatesObservations() throws IOException {
        // Given: old tree with duplicate observations
        TreeSnapshot oldTree = new TreeSnapshot();
        oldTree.setBranches(new EnumMap<>(TreeBranch.class));
        oldTree.getBranches().put(TreeBranch.COMMUNICATION, List.of(
                new ObservationLeaf("aime discuter", TreeBranch.COMMUNICATION, ObservationSource.LLM_EXTRACTED),
                new ObservationLeaf("aime discuter", TreeBranch.COMMUNICATION, ObservationSource.LLM_EXTRACTED),
                new ObservationLeaf("très bavard", TreeBranch.COMMUNICATION, ObservationSource.LLM_EXTRACTED)
        ));

        writeOldTree(oldTree);

        // When: migrate
        migrator.migrateIfNeeded();

        // Then: duplicates should be removed
        String value = treeService.getLeafValue(PATH_COMMUNICATION);
        assertNotNull(value);

        // Count occurrences of "aime discuter" — should be only 1
        int firstIndex = value.indexOf("aime discuter");
        int lastIndex = value.lastIndexOf("aime discuter");
        assertEquals(firstIndex, lastIndex, "Should have only one occurrence of 'aime discuter'");

        assertTrue(value.contains("très bavard"), "Should contain the other unique observation");
        assertTrue(value.contains(" ; "), "Should use ';' separator");
    }

    @Test
    void skipsIfPersonaTreeAlreadyExists() throws IOException {
        // Given: persona-tree.json already exists with a pre-set value
        treeService.setLeafValue(PATH_COMMUNICATION, "existing value");
        treeService.persist();

        // And: old tree with different content
        TreeSnapshot oldTree = new TreeSnapshot();
        oldTree.setBranches(new EnumMap<>(TreeBranch.class));
        oldTree.getBranches().put(TreeBranch.COMMUNICATION, List.of(
                new ObservationLeaf("should not appear", TreeBranch.COMMUNICATION, ObservationSource.LLM_EXTRACTED)
        ));
        writeOldTree(oldTree);

        // When: migrate
        migrator.migrateIfNeeded();

        // Then: pre-set value should NOT be overwritten (proves migration didn't run)
        String value = treeService.getLeafValue(PATH_COMMUNICATION);
        assertEquals("existing value", value, "Should preserve existing value, not migrate");
        assertFalse(value.contains("should not appear"), "Should not have migrated old tree content");
    }

    @Test
    void skipsIfOldTreeDoesNotExist() {
        // Given: no old tree file exists (default state)
        assertFalse(Files.exists(Path.of(properties.getStoragePath())), "Old tree should not exist");

        // When: migrate
        migrator.migrateIfNeeded();

        // Then: migration completes without error, 0 non-empty leaves
        assertEquals(0, treeService.getNonEmptyLeafCount(), "Should have 0 filled leaves");

        // And persona tree should NOT be created (nothing to persist)
        assertFalse(Files.exists(Path.of(properties.getPersonaTreePath())),
                "Should not create persona tree when nothing to migrate");
    }

    @Test
    void handlesEmptyBranches() throws IOException {
        // Given: old tree with empty branch lists
        TreeSnapshot oldTree = new TreeSnapshot();
        oldTree.setBranches(new EnumMap<>(TreeBranch.class));
        oldTree.getBranches().put(TreeBranch.IDENTITE, new ArrayList<>());
        oldTree.getBranches().put(TreeBranch.COMMUNICATION, null);
        oldTree.getBranches().put(TreeBranch.HABITUDES, List.of());

        writeOldTree(oldTree);

        // When: migrate
        migrator.migrateIfNeeded();

        // Then: migration completes, 0 leaves filled
        assertEquals(0, treeService.getNonEmptyLeafCount(), "Empty branches should result in 0 filled leaves");

        // All target paths should be empty
        assertEquals("", treeService.getLeafValue(PATH_IDENTITE));
        assertEquals("", treeService.getLeafValue(PATH_COMMUNICATION));
        assertEquals("", treeService.getLeafValue(PATH_HABITUDES));
    }

    @Test
    void handlesNullAndEmptyTexts() throws IOException {
        // Given: old tree with null and empty text observations
        ObservationLeaf leafWithNull = new ObservationLeaf();
        leafWithNull.setText(null);
        leafWithNull.setBranch(TreeBranch.INTERETS);
        leafWithNull.setSource(ObservationSource.LLM_EXTRACTED);

        ObservationLeaf leafWithEmpty = new ObservationLeaf();
        leafWithEmpty.setText("");
        leafWithEmpty.setBranch(TreeBranch.INTERETS);
        leafWithEmpty.setSource(ObservationSource.LLM_EXTRACTED);

        ObservationLeaf leafWithValidText = new ObservationLeaf("lecture", TreeBranch.INTERETS, ObservationSource.LLM_EXTRACTED);

        TreeSnapshot oldTree = new TreeSnapshot();
        oldTree.setBranches(new EnumMap<>(TreeBranch.class));
        oldTree.getBranches().put(TreeBranch.INTERETS, List.of(leafWithNull, leafWithEmpty, leafWithValidText));

        writeOldTree(oldTree);

        // When: migrate
        migrator.migrateIfNeeded();

        // Then: only the valid text should be migrated
        String value = treeService.getLeafValue(PATH_INTERETS);
        assertEquals("lecture", value, "Should only migrate the valid text, ignoring null/empty");
    }

    @Test
    void migratesAllSixBranches() throws IOException {
        // Given: old tree with observations in all six branches
        TreeSnapshot oldTree = new TreeSnapshot();
        oldTree.setBranches(new EnumMap<>(TreeBranch.class));
        oldTree.getBranches().put(TreeBranch.IDENTITE, List.of(
                new ObservationLeaf("s'appelle Pierre", TreeBranch.IDENTITE, ObservationSource.LLM_EXTRACTED)
        ));
        oldTree.getBranches().put(TreeBranch.COMMUNICATION, List.of(
                new ObservationLeaf("ton amical", TreeBranch.COMMUNICATION, ObservationSource.LLM_EXTRACTED)
        ));
        oldTree.getBranches().put(TreeBranch.HABITUDES, List.of(
                new ObservationLeaf("se lève tôt", TreeBranch.HABITUDES, ObservationSource.LLM_EXTRACTED)
        ));
        oldTree.getBranches().put(TreeBranch.OBJECTIFS, List.of(
                new ObservationLeaf("apprendre l'IA", TreeBranch.OBJECTIFS, ObservationSource.LLM_EXTRACTED)
        ));
        oldTree.getBranches().put(TreeBranch.EMOTIONS, List.of(
                new ObservationLeaf("optimiste", TreeBranch.EMOTIONS, ObservationSource.LLM_EXTRACTED)
        ));
        oldTree.getBranches().put(TreeBranch.INTERETS, List.of(
                new ObservationLeaf("musique", TreeBranch.INTERETS, ObservationSource.LLM_EXTRACTED)
        ));

        writeOldTree(oldTree);

        // When: migrate
        migrator.migrateIfNeeded();

        // Then: all six target leaves should be filled
        assertEquals(6, treeService.getNonEmptyLeafCount(), "All 6 branches should be migrated");

        assertEquals("s'appelle Pierre", treeService.getLeafValue(PATH_IDENTITE));
        assertEquals("ton amical", treeService.getLeafValue(PATH_COMMUNICATION));
        assertEquals("se lève tôt", treeService.getLeafValue(PATH_HABITUDES));
        assertEquals("apprendre l'IA", treeService.getLeafValue(PATH_OBJECTIFS));
        assertEquals("optimiste", treeService.getLeafValue(PATH_EMOTIONS));
        assertEquals("musique", treeService.getLeafValue(PATH_INTERETS));
    }

    // ========== Helper Methods ==========

    /**
     * Write an old TreeSnapshot to the old tree path.
     */
    private void writeOldTree(TreeSnapshot snapshot) throws IOException {
        Path oldTreePath = Path.of(properties.getStoragePath());
        Files.createDirectories(oldTreePath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(oldTreePath.toFile(), snapshot);
    }

    /**
     * TreeSnapshot structure matching old UserTreePersistenceService.
     */
    private static class TreeSnapshot {
        private Map<TreeBranch, List<ObservationLeaf>> branches;
        private int conversationCount;
        private Map<TreeBranch, String> summaries;

        public Map<TreeBranch, List<ObservationLeaf>> getBranches() {
            return branches;
        }

        public void setBranches(Map<TreeBranch, List<ObservationLeaf>> branches) {
            this.branches = branches;
        }

        public int getConversationCount() {
            return conversationCount;
        }

        public void setConversationCount(int conversationCount) {
            this.conversationCount = conversationCount;
        }

        public Map<TreeBranch, String> getSummaries() {
            return summaries;
        }

        public void setSummaries(Map<TreeBranch, String> summaries) {
            this.summaries = summaries;
        }
    }
}
