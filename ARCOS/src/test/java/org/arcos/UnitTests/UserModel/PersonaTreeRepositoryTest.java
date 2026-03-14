package org.arcos.UnitTests.UserModel;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.arcos.UserModel.PersonaTree.*;
import org.arcos.UserModel.UserModelProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PersonaTreeRepositoryTest {

    private PersonaTreeRepository repository;
    private PersonaTreeSchemaLoader schemaLoader;
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        repository = new PersonaTreeRepository();
        objectMapper = new ObjectMapper();

        // Initialize schema loader to create test trees
        UserModelProperties properties = new UserModelProperties();
        properties.setPersonaTreeSchemaPath("persona-tree-schema.json");
        properties.setPersonaTreePath("data/persona-tree.json");
        schemaLoader = new PersonaTreeSchemaLoader(properties);
        schemaLoader.init();
    }

    @Test
    void saveAndLoadRoundtrip() {
        // Given: Create a tree with some values
        PersonaTree tree = schemaLoader.loadSchema();
        PersonaNode scalpHairNode = navigateToLeaf(tree, "1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair");
        scalpHairNode.setValue("châtain foncé");
        tree.setConversationCount(42);

        Path filePath = tempDir.resolve("test-tree.json");

        // When: Save and load
        repository.save(tree, filePath);
        Optional<PersonaTree> loaded = repository.load(filePath);

        // Then: Verify the tree was loaded correctly
        assertTrue(loaded.isPresent(), "Tree should be loaded");
        PersonaTree loadedTree = loaded.get();
        assertEquals(42, loadedTree.getConversationCount(), "Conversation count should match");

        // Verify the leaf value
        PersonaNode loadedScalpHair = navigateToLeaf(loadedTree, "1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair");
        assertNotNull(loadedScalpHair, "Leaf should exist in loaded tree");
        assertTrue(loadedScalpHair.isLeaf(), "Should be a leaf node");
        assertEquals("châtain foncé", loadedScalpHair.getValue(), "Leaf value should match");
    }

    @Test
    void loadReturnsEmptyForMissingFile() {
        // Given: A path to a non-existent file
        Path nonExistentPath = tempDir.resolve("does-not-exist.json");

        // When: Load
        Optional<PersonaTree> result = repository.load(nonExistentPath);

        // Then: Should return empty
        assertFalse(result.isPresent(), "Should return empty for missing file");
    }

    @Test
    void loadReturnsEmptyForCorruptedFile() throws IOException {
        // Given: A file with garbage content
        Path corruptedFile = tempDir.resolve("corrupted.json");
        Files.writeString(corruptedFile, "{ this is not valid JSON at all !@#$%");

        // When: Load
        Optional<PersonaTree> result = repository.load(corruptedFile);

        // Then: Should return empty instead of throwing
        assertFalse(result.isPresent(), "Should return empty for corrupted file");
    }

    @Test
    void saveCreatesParentDirectories() {
        // Given: A tree and a nested path that doesn't exist yet
        PersonaTree tree = schemaLoader.loadSchema();
        tree.setConversationCount(7);
        Path nestedPath = tempDir.resolve("nested/deep/path/tree.json");

        // When: Save
        repository.save(tree, nestedPath);

        // Then: File should exist
        assertTrue(Files.exists(nestedPath), "File should be created in nested directory");

        // Verify it's valid
        Optional<PersonaTree> loaded = repository.load(nestedPath);
        assertTrue(loaded.isPresent(), "Should load from nested path");
        assertEquals(7, loaded.get().getConversationCount(), "Content should match");
    }

    @Test
    void atomicWriteDoesNotLeaveTmpFile() {
        // Given: A tree to save
        PersonaTree tree = schemaLoader.loadSchema();
        Path filePath = tempDir.resolve("atomic-test.json");

        // When: Save
        repository.save(tree, filePath);

        // Then: Main file should exist
        assertTrue(Files.exists(filePath), "Main file should exist");

        // And tmp file should NOT exist
        Path tmpPath = Path.of(filePath + ".tmp");
        assertFalse(Files.exists(tmpPath), "Temporary file should not exist after save");
    }

    @Test
    void createSnapshotCreatesTimestampedFile() {
        // Given: A tree with some data
        PersonaTree tree = schemaLoader.loadSchema();
        PersonaNode hairNode = navigateToLeaf(tree, "1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair");
        hairNode.setValue("blond platine");
        tree.setConversationCount(99);

        // When: Create snapshot
        Path snapshotPath = repository.createSnapshot(tree, tempDir);

        // Then: Snapshot file should exist
        assertTrue(Files.exists(snapshotPath), "Snapshot file should exist");

        // Verify filename pattern (persona-tree-snapshot-<timestamp>.json)
        String filename = snapshotPath.getFileName().toString();
        assertTrue(filename.startsWith("persona-tree-snapshot-"), "Filename should have correct prefix");
        assertTrue(filename.endsWith(".json"), "Filename should have .json extension");

        // Verify content is correct
        Optional<PersonaTree> loaded = repository.load(snapshotPath);
        assertTrue(loaded.isPresent(), "Snapshot should be loadable");
        assertEquals(99, loaded.get().getConversationCount(), "Snapshot should have correct conversation count");

        PersonaNode loadedHair = navigateToLeaf(loaded.get(), "1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair");
        assertEquals("blond platine", loadedHair.getValue(), "Snapshot should contain leaf values");
    }

    @Test
    void loadHandlesMissingTreeKey() throws IOException {
        // Given: A file with conversationCount but no tree key
        Path filePath = tempDir.resolve("no-tree-key.json");
        Map<String, Object> invalidWrapper = Map.of("conversationCount", 5);
        objectMapper.writeValue(filePath.toFile(), invalidWrapper);

        // When: Load
        Optional<PersonaTree> result = repository.load(filePath);

        // Then: Should return empty and log warning
        assertFalse(result.isPresent(), "Should return empty when tree key is missing");
    }

    @Test
    void loadDefaultsConversationCountToZeroIfMissing() throws IOException {
        // Given: Create a minimal valid tree structure without conversationCount
        PersonaTree tree = schemaLoader.loadSchema();
        Path filePath = tempDir.resolve("no-count.json");

        // Write only the tree part manually
        Map<String, Object> wrapper = new LinkedHashMap<>();
        // No conversationCount key
        wrapper.put("tree", tree.getRoots());
        objectMapper.writeValue(filePath.toFile(), wrapper);

        // When: Load
        Optional<PersonaTree> loaded = repository.load(filePath);

        // Then: Should load with conversation count = 0
        assertTrue(loaded.isPresent(), "Should load successfully");
        assertEquals(0, loaded.get().getConversationCount(), "Should default to 0 when conversationCount is missing");
    }

    @Test
    void saveAndLoadPreservesMultipleLeafValues() {
        // Given: A tree with multiple values set
        PersonaTree tree = schemaLoader.loadSchema();

        PersonaNode hair = navigateToLeaf(tree, "1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair");
        hair.setValue("roux");

        PersonaNode facialHair = navigateToLeaf(tree, "1_Biological_Characteristics.Physical_Appearance.Hair.Facial_Hair");
        facialHair.setValue("barbe épaisse");

        PersonaNode intelligence = navigateToLeaf(tree, "2_Psychological_Characteristics.Cognitive_Abilities.Intelligence_Level");
        intelligence.setValue("très élevé");

        tree.setConversationCount(123);

        Path filePath = tempDir.resolve("multiple-values.json");

        // When: Save and load
        repository.save(tree, filePath);
        Optional<PersonaTree> loaded = repository.load(filePath);

        // Then: All values should be preserved
        assertTrue(loaded.isPresent(), "Tree should be loaded");
        PersonaTree loadedTree = loaded.get();
        assertEquals(123, loadedTree.getConversationCount(), "Conversation count should match");

        PersonaNode loadedHair = navigateToLeaf(loadedTree, "1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair");
        assertEquals("roux", loadedHair.getValue(), "First leaf value should match");

        PersonaNode loadedFacialHair = navigateToLeaf(loadedTree, "1_Biological_Characteristics.Physical_Appearance.Hair.Facial_Hair");
        assertEquals("barbe épaisse", loadedFacialHair.getValue(), "Second leaf value should match");

        PersonaNode loadedIntelligence = navigateToLeaf(loadedTree, "2_Psychological_Characteristics.Cognitive_Abilities.Intelligence_Level");
        assertEquals("très élevé", loadedIntelligence.getValue(), "Third leaf value should match");
    }

    // Helper method to navigate to a leaf in a tree
    private PersonaNode navigateToLeaf(PersonaTree tree, String dotPath) {
        String[] segments = dotPath.split("\\.");
        PersonaNode current = tree.getRoots().get(segments[0]);
        assertNotNull(current, "Root node should exist for path: " + dotPath);

        for (int i = 1; i < segments.length; i++) {
            assertFalse(current.isLeaf(), "Should not hit a leaf before reaching target in path: " + dotPath);
            current = current.getChildren().get(segments[i]);
            assertNotNull(current, "Node should exist at segment " + i + " in path: " + dotPath);
        }

        return current;
    }
}
