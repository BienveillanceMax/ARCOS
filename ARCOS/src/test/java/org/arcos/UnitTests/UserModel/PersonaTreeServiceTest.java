package org.arcos.UnitTests.UserModel;

import org.arcos.UserModel.PersonaTree.*;
import org.arcos.UserModel.UserModelProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PersonaTreeServiceTest {

    private PersonaTreeService service;
    private PersonaTreeSchemaLoader schemaLoader;
    private PersonaTreeRepository repository;
    private UserModelProperties properties;

    // Test paths from the schema
    private static final String PATH_HAIR_SCALP = "1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair";
    private static final String PATH_HAIR_FACIAL = "1_Biological_Characteristics.Physical_Appearance.Hair.Facial_Hair";
    private static final String PATH_INTELLIGENCE = "2_Psychological_Characteristics.Cognitive_Abilities.Intelligence_Level";

    @BeforeEach
    void setUp() {
        // Create real schema loader with properties
        properties = new UserModelProperties();
        properties.setPersonaTreeSchemaPath("persona-tree-schema.json");
        properties.setPersonaTreePath("data/persona-tree.json");

        schemaLoader = new PersonaTreeSchemaLoader(properties);
        schemaLoader.init(); // Load the real schema

        // Mock repository
        repository = mock(PersonaTreeRepository.class);
        when(repository.load(any(Path.class))).thenReturn(Optional.empty()); // No existing file

        // Create service
        service = new PersonaTreeService(schemaLoader, repository, properties);
        service.initialize();
    }

    @Test
    void getLeafValueReturnsEmptyForUnsetLeaf() {
        String value = service.getLeafValue(PATH_HAIR_SCALP);
        assertNotNull(value, "Valid leaf path should return non-null value");
        assertEquals("", value, "Unset leaf should return empty string");
    }

    @Test
    void setAndGetLeafValue() {
        service.setLeafValue(PATH_HAIR_SCALP, "brun");
        String value = service.getLeafValue(PATH_HAIR_SCALP);
        assertEquals("brun", value, "Should retrieve the value that was set");
    }

    @Test
    void setLeafValueRejectsInvalidPath() {
        assertThrows(IllegalArgumentException.class,
                () -> service.setLeafValue("Invalid.Path.That.Does.Not.Exist", "value"),
                "Should throw exception for invalid path");
    }

    @Test
    void getLeafValueReturnsNullForInvalidPath() {
        String value = service.getLeafValue("Invalid.Path.That.Does.Not.Exist");
        assertNull(value, "Invalid path should return null");
    }

    @Test
    void clearLeafValueResetsToEmpty() {
        // Set a value
        service.setLeafValue(PATH_HAIR_SCALP, "blond");
        assertEquals("blond", service.getLeafValue(PATH_HAIR_SCALP));

        // Clear it
        service.clearLeafValue(PATH_HAIR_SCALP);
        assertEquals("", service.getLeafValue(PATH_HAIR_SCALP), "Cleared leaf should be empty string");
    }

    @Test
    void getNonEmptyLeavesReturnsOnlyFilledLeaves() {
        // Initially empty
        Map<String, String> leaves = service.getNonEmptyLeaves();
        assertEquals(0, leaves.size(), "Should start with no filled leaves");

        // Set two values
        service.setLeafValue(PATH_HAIR_SCALP, "châtain");
        service.setLeafValue(PATH_INTELLIGENCE, "moyen");

        leaves = service.getNonEmptyLeaves();
        assertEquals(2, leaves.size(), "Should return exactly 2 filled leaves");
        assertEquals("châtain", leaves.get(PATH_HAIR_SCALP));
        assertEquals("moyen", leaves.get(PATH_INTELLIGENCE));
    }

    @Test
    void getLeavesUnderPathReturnsBranchSubset() {
        // Set values in different branches
        service.setLeafValue(PATH_HAIR_SCALP, "roux");
        service.setLeafValue(PATH_HAIR_FACIAL, "barbe courte");
        service.setLeafValue(PATH_INTELLIGENCE, "élevé");

        // Query only Hair branch
        Map<String, String> hairLeaves = service.getLeavesUnderPath("1_Biological_Characteristics.Physical_Appearance.Hair");
        assertEquals(2, hairLeaves.size(), "Should return only Hair leaves");
        assertTrue(hairLeaves.containsKey(PATH_HAIR_SCALP), "Should contain Scalp_Hair");
        assertTrue(hairLeaves.containsKey(PATH_HAIR_FACIAL), "Should contain Facial_Hair");
        assertFalse(hairLeaves.containsKey(PATH_INTELLIGENCE), "Should not contain Psychology leaves");

        // Query Psychology branch
        Map<String, String> psychLeaves = service.getLeavesUnderPath("2_Psychological_Characteristics");
        assertEquals(1, psychLeaves.size(), "Should return only Psychology leaves");
        assertTrue(psychLeaves.containsKey(PATH_INTELLIGENCE), "Should contain Intelligence_Level");
    }

    @Test
    void getNonEmptyLeafCount() {
        assertEquals(0, service.getNonEmptyLeafCount(), "Should start at 0");

        service.setLeafValue(PATH_HAIR_SCALP, "noir");
        assertEquals(1, service.getNonEmptyLeafCount(), "Should count 1 after setting one leaf");

        service.setLeafValue(PATH_INTELLIGENCE, "très élevé");
        assertEquals(2, service.getNonEmptyLeafCount(), "Should count 2 after setting two leaves");

        service.clearLeafValue(PATH_HAIR_SCALP);
        assertEquals(1, service.getNonEmptyLeafCount(), "Should count 1 after clearing one leaf");
    }

    @Test
    void conversationCountStartsAtZero() {
        assertEquals(0, service.getConversationCount(), "Conversation count should start at 0");
    }

    @Test
    void incrementConversationCount() {
        service.incrementConversationCount();
        assertEquals(1, service.getConversationCount(), "Should be 1 after one increment");

        service.incrementConversationCount();
        assertEquals(2, service.getConversationCount(), "Should be 2 after two increments");
    }

    @Test
    void persistCreatesDeepCopyAndSaves() {
        // Set a value
        service.setLeafValue(PATH_HAIR_SCALP, "auburn");
        service.incrementConversationCount();

        // Call persist
        service.persist();

        // Verify repository.save was called
        ArgumentCaptor<PersonaTree> treeCaptor = ArgumentCaptor.forClass(PersonaTree.class);
        ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);
        verify(repository, times(1)).save(treeCaptor.capture(), pathCaptor.capture());

        // Check the captured tree has the correct value
        PersonaTree savedTree = treeCaptor.getValue();
        assertNotNull(savedTree, "Saved tree should not be null");
        assertEquals(1, savedTree.getConversationCount(), "Saved tree should have conversation count 1");

        // Navigate to the leaf in the saved tree to verify the value
        String[] segments = PATH_HAIR_SCALP.split("\\.");
        PersonaNode current = savedTree.getRoots().get(segments[0]);
        assertNotNull(current, "Root node should exist in saved tree");

        for (int i = 1; i < segments.length; i++) {
            current = current.getChildren().get(segments[i]);
            assertNotNull(current, "Intermediate node should exist in saved tree");
        }

        assertTrue(current.isLeaf(), "Final node should be a leaf");
        assertEquals("auburn", current.getValue(), "Saved tree should contain the set value");

        // Check the path
        Path savedPath = pathCaptor.getValue();
        assertEquals(Paths.get("data/persona-tree.json"), savedPath, "Should save to configured path");
    }

    @Test
    void initializeLoadsExistingTree() {
        // Create a tree with some data
        PersonaTree existingTree = schemaLoader.loadSchema();
        PersonaNode scalpHairNode = navigateToLeafInTree(existingTree, PATH_HAIR_SCALP);
        scalpHairNode.setValue("gris");
        existingTree.setConversationCount(5);

        // Mock repository to return existing tree
        PersonaTreeRepository mockRepo = mock(PersonaTreeRepository.class);
        when(mockRepo.load(any(Path.class))).thenReturn(Optional.of(existingTree));

        // Create new service and initialize
        PersonaTreeService newService = new PersonaTreeService(schemaLoader, mockRepo, properties);
        newService.initialize();

        // Verify loaded values
        assertEquals(5, newService.getConversationCount(), "Should load conversation count");
        assertEquals("gris", newService.getLeafValue(PATH_HAIR_SCALP), "Should load leaf value");
    }

    // Helper to navigate to a leaf in a tree for testing
    private PersonaNode navigateToLeafInTree(PersonaTree tree, String dotPath) {
        String[] segments = dotPath.split("\\.");
        PersonaNode current = tree.getRoots().get(segments[0]);
        for (int i = 1; i < segments.length; i++) {
            current = current.getChildren().get(segments[i]);
        }
        return current;
    }
}
