package org.arcos.UnitTests.UserModel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.arcos.UserModel.PersonaTree.*;
import org.arcos.UserModel.UserModelProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PersonaTreeMigrator.
 * Uses real PersonaTreeService/Repository/SchemaLoader with @TempDir.
 * Writes old tree format as raw JSON since v1 model classes are deleted.
 */
class PersonaTreeMigratorTest {

    @TempDir
    Path tempDir;

    private PersonaTreeMigrator migrator;
    private PersonaTreeService treeService;
    private UserModelProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PATH_IDENTITE = "2_Psychological_Characteristics.Self_Perception.Identity.Clarity";
    private static final String PATH_COMMUNICATION = "5_Behavioral_Characteristics.Social_Interactions.Communication_Style.Tendencies";
    private static final String PATH_HABITUDES = "5_Behavioral_Characteristics.Behavioral_Habits.Daily_Routine";
    private static final String PATH_OBJECTIFS = "4_Identity_Characteristics.Motivations_and_Goals.Goals.Long_Term";
    private static final String PATH_EMOTIONS = "2_Psychological_Characteristics.Psychological_State.Emotional_Baseline";
    private static final String PATH_INTERETS = "5_Behavioral_Characteristics.Interests_and_Skills.Interests_and_Hobbies";

    @BeforeEach
    void setUp() {
        properties = new UserModelProperties();
        properties.setPersonaTreeSchemaPath("persona-tree-schema.json");
        properties.setStoragePath(tempDir.resolve("user-tree.json").toString());
        properties.setPersonaTreePath(tempDir.resolve("persona-tree.json").toString());

        PersonaTreeSchemaLoader schemaLoader = new PersonaTreeSchemaLoader(properties);
        schemaLoader.init();
        PersonaTreeRepository repository = new PersonaTreeRepository();
        treeService = new PersonaTreeService(schemaLoader, repository, properties);
        treeService.initialize();

        migrator = new PersonaTreeMigrator(treeService, properties);
    }

    @Test
    void migratesOldTreeToPersonaTree() throws IOException {
        ObjectNode tree = objectMapper.createObjectNode();
        ObjectNode branches = tree.putObject("branches");
        ArrayNode comm = branches.putArray("COMMUNICATION");
        comm.add(obs("parle vite"));
        comm.add(obs("aime les blagues"));
        ArrayNode emo = branches.putArray("EMOTIONS");
        emo.add(obs("calme en général"));
        writeOldTree(tree);

        migrator.migrateIfNeeded();

        String commValue = treeService.getLeafValue(PATH_COMMUNICATION);
        assertNotNull(commValue);
        assertTrue(commValue.contains("parle vite"));
        assertTrue(commValue.contains("aime les blagues"));
        assertTrue(commValue.contains(" ; "));

        assertEquals("calme en général", treeService.getLeafValue(PATH_EMOTIONS));
        assertTrue(Files.exists(Path.of(properties.getPersonaTreePath())));
    }

    @Test
    void deduplicatesObservations() throws IOException {
        ObjectNode tree = objectMapper.createObjectNode();
        ObjectNode branches = tree.putObject("branches");
        ArrayNode comm = branches.putArray("COMMUNICATION");
        comm.add(obs("aime discuter"));
        comm.add(obs("aime discuter"));
        comm.add(obs("très bavard"));
        writeOldTree(tree);

        migrator.migrateIfNeeded();

        String value = treeService.getLeafValue(PATH_COMMUNICATION);
        assertNotNull(value);
        int firstIndex = value.indexOf("aime discuter");
        int lastIndex = value.lastIndexOf("aime discuter");
        assertEquals(firstIndex, lastIndex, "Should deduplicate");
        assertTrue(value.contains("très bavard"));
    }

    @Test
    void skipsIfPersonaTreeAlreadyExists() throws IOException {
        treeService.setLeafValue(PATH_COMMUNICATION, "existing value");
        treeService.persist();

        ObjectNode tree = objectMapper.createObjectNode();
        ObjectNode branches = tree.putObject("branches");
        ArrayNode comm = branches.putArray("COMMUNICATION");
        comm.add(obs("should not appear"));
        writeOldTree(tree);

        migrator.migrateIfNeeded();

        assertEquals("existing value", treeService.getLeafValue(PATH_COMMUNICATION));
    }

    @Test
    void skipsIfOldTreeDoesNotExist() {
        assertFalse(Files.exists(Path.of(properties.getStoragePath())));
        migrator.migrateIfNeeded();
        assertEquals(0, treeService.getNonEmptyLeafCount());
    }

    @Test
    void handlesEmptyBranches() throws IOException {
        ObjectNode tree = objectMapper.createObjectNode();
        ObjectNode branches = tree.putObject("branches");
        branches.putArray("IDENTITE");
        branches.putNull("COMMUNICATION");
        branches.putArray("HABITUDES");
        writeOldTree(tree);

        migrator.migrateIfNeeded();

        assertEquals(0, treeService.getNonEmptyLeafCount());
    }

    @Test
    void handlesNullAndEmptyTexts() throws IOException {
        ObjectNode tree = objectMapper.createObjectNode();
        ObjectNode branches = tree.putObject("branches");
        ArrayNode arr = branches.putArray("INTERETS");
        ObjectNode nullText = objectMapper.createObjectNode();
        nullText.putNull("text");
        arr.add(nullText);
        ObjectNode emptyText = objectMapper.createObjectNode();
        emptyText.put("text", "");
        arr.add(emptyText);
        arr.add(obs("lecture"));
        writeOldTree(tree);

        migrator.migrateIfNeeded();

        assertEquals("lecture", treeService.getLeafValue(PATH_INTERETS));
    }

    @Test
    void migratesAllSixBranches() throws IOException {
        ObjectNode tree = objectMapper.createObjectNode();
        ObjectNode branches = tree.putObject("branches");
        addBranch(branches, "IDENTITE", "s'appelle Pierre");
        addBranch(branches, "COMMUNICATION", "ton amical");
        addBranch(branches, "HABITUDES", "se lève tôt");
        addBranch(branches, "OBJECTIFS", "apprendre l'IA");
        addBranch(branches, "EMOTIONS", "optimiste");
        addBranch(branches, "INTERETS", "musique");
        writeOldTree(tree);

        migrator.migrateIfNeeded();

        assertEquals(6, treeService.getNonEmptyLeafCount());
        assertEquals("s'appelle Pierre", treeService.getLeafValue(PATH_IDENTITE));
        assertEquals("ton amical", treeService.getLeafValue(PATH_COMMUNICATION));
        assertEquals("se lève tôt", treeService.getLeafValue(PATH_HABITUDES));
        assertEquals("apprendre l'IA", treeService.getLeafValue(PATH_OBJECTIFS));
        assertEquals("optimiste", treeService.getLeafValue(PATH_EMOTIONS));
        assertEquals("musique", treeService.getLeafValue(PATH_INTERETS));
    }

    // ========== Helpers ==========

    private ObjectNode obs(String text) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("text", text);
        node.put("branch", "IDENTITE");
        node.put("source", "LLM_EXTRACTED");
        return node;
    }

    private void addBranch(ObjectNode branches, String branchName, String... texts) {
        ArrayNode arr = branches.putArray(branchName);
        for (String text : texts) {
            arr.add(obs(text));
        }
    }

    private void writeOldTree(ObjectNode tree) throws IOException {
        Path oldTreePath = Path.of(properties.getStoragePath());
        Files.createDirectories(oldTreePath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(oldTreePath.toFile(), tree);
    }
}
