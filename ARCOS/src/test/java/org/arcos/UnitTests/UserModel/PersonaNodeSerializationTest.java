package org.arcos.UnitTests.UserModel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.arcos.UserModel.PersonaTree.PersonaNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

class PersonaNodeSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void deserializeLeafNode() throws IOException {
        // Given
        String json = "\"test value\"";

        // When
        PersonaNode node = objectMapper.readValue(json, PersonaNode.class);

        // Then
        assertTrue(node.isLeaf());
        assertEquals("test value", node.getValue());
        assertNull(node.getChildren());
    }

    @Test
    void deserializeBranchNode() throws IOException {
        // Given
        String json = """
            {
              "child1": "value1",
              "child2": {
                "grandchild1": "value2",
                "grandchild2": "value3"
              }
            }
            """;

        // When
        PersonaNode node = objectMapper.readValue(json, PersonaNode.class);

        // Then
        assertFalse(node.isLeaf());
        assertNull(node.getValue());
        assertNotNull(node.getChildren());
        assertEquals(2, node.getChildren().size());

        // Verify child1 is a leaf
        PersonaNode child1 = node.getChildren().get("child1");
        assertTrue(child1.isLeaf());
        assertEquals("value1", child1.getValue());

        // Verify child2 is a branch
        PersonaNode child2 = node.getChildren().get("child2");
        assertFalse(child2.isLeaf());
        assertEquals(2, child2.getChildren().size());

        // Verify grandchildren
        assertEquals("value2", child2.getChildren().get("grandchild1").getValue());
        assertEquals("value3", child2.getChildren().get("grandchild2").getValue());
    }

    @Test
    void serializeLeafNode() throws IOException {
        // Given
        PersonaNode node = PersonaNode.leaf("test value");

        // When
        String json = objectMapper.writeValueAsString(node);

        // Then
        assertEquals("\"test value\"", json);
    }

    @Test
    void serializeBranchNode() throws IOException {
        // Given
        LinkedHashMap<String, PersonaNode> children = new LinkedHashMap<>();
        children.put("child1", PersonaNode.leaf("value1"));

        LinkedHashMap<String, PersonaNode> grandchildren = new LinkedHashMap<>();
        grandchildren.put("grandchild1", PersonaNode.leaf("value2"));
        grandchildren.put("grandchild2", PersonaNode.leaf("value3"));
        children.put("child2", PersonaNode.branch(grandchildren));

        PersonaNode node = PersonaNode.branch(children);

        // When
        String json = objectMapper.writeValueAsString(node);

        // Then
        assertTrue(json.contains("\"child1\":\"value1\""));
        assertTrue(json.contains("\"child2\":{"));
        assertTrue(json.contains("\"grandchild1\":\"value2\""));
        assertTrue(json.contains("\"grandchild2\":\"value3\""));
    }

    @Test
    void roundtripFullSchema() throws IOException {
        // Given - load the persona-tree-schema.json from resources
        InputStream schemaStream = getClass().getClassLoader().getResourceAsStream("persona-tree-schema.json");
        assertNotNull(schemaStream, "persona-tree-schema.json not found in resources");

        TypeReference<LinkedHashMap<String, PersonaNode>> typeRef =
            new TypeReference<LinkedHashMap<String, PersonaNode>>() {};

        // When - deserialize
        LinkedHashMap<String, PersonaNode> personaTree = objectMapper.readValue(schemaStream, typeRef);

        // Then - verify structure
        assertNotNull(personaTree);
        assertEquals(5, personaTree.size(), "Should have 5 root categories");

        // Verify root category names
        assertTrue(personaTree.containsKey("1_Biological_Characteristics"));
        assertTrue(personaTree.containsKey("2_Psychological_Characteristics"));
        assertTrue(personaTree.containsKey("3_Personality_Characteristics"));
        assertTrue(personaTree.containsKey("4_Identity_Characteristics"));
        assertTrue(personaTree.containsKey("5_Behavioral_Characteristics"));

        // Verify a specific path: 1_Biological_Characteristics > Physical_Appearance > Body_Build > Height
        PersonaNode biologicalNode = personaTree.get("1_Biological_Characteristics");
        assertFalse(biologicalNode.isLeaf());

        PersonaNode physicalAppearance = biologicalNode.getChildren().get("Physical_Appearance");
        assertFalse(physicalAppearance.isLeaf());

        PersonaNode bodyBuild = physicalAppearance.getChildren().get("Body_Build");
        assertFalse(bodyBuild.isLeaf());

        PersonaNode height = bodyBuild.getChildren().get("Height");
        assertTrue(height.isLeaf());
        assertEquals("", height.getValue());

        // When - serialize back
        String serialized = objectMapper.writeValueAsString(personaTree);

        // Then - deserialize again and verify structure is preserved
        LinkedHashMap<String, PersonaNode> roundtrip = objectMapper.readValue(serialized, typeRef);
        assertEquals(5, roundtrip.size());
        assertTrue(roundtrip.containsKey("1_Biological_Characteristics"));

        // Verify the same path exists after roundtrip
        PersonaNode rtBiological = roundtrip.get("1_Biological_Characteristics");
        PersonaNode rtPhysical = rtBiological.getChildren().get("Physical_Appearance");
        PersonaNode rtBodyBuild = rtPhysical.getChildren().get("Body_Build");
        PersonaNode rtHeight = rtBodyBuild.getChildren().get("Height");
        assertTrue(rtHeight.isLeaf());
        assertEquals("", rtHeight.getValue());
    }

    @Test
    void setValueOnBranchThrows() {
        // Given
        LinkedHashMap<String, PersonaNode> children = new LinkedHashMap<>();
        children.put("child", PersonaNode.leaf("value"));
        PersonaNode branch = PersonaNode.branch(children);

        // When/Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> branch.setValue("new value")
        );
        assertEquals("Cannot set value on a branch node", exception.getMessage());
    }

    @Test
    void deepCopyIsIndependent() {
        // Given - create a tree with branch and leaf
        LinkedHashMap<String, PersonaNode> children = new LinkedHashMap<>();
        children.put("child1", PersonaNode.leaf("original value"));

        LinkedHashMap<String, PersonaNode> grandchildren = new LinkedHashMap<>();
        grandchildren.put("grandchild", PersonaNode.leaf("grandchild value"));
        children.put("child2", PersonaNode.branch(grandchildren));

        PersonaNode original = PersonaNode.branch(children);

        // When - deep copy and modify the copy
        PersonaNode copy = original.deepCopy();
        copy.getChildren().get("child1").setValue("modified value");
        copy.getChildren().get("child2").getChildren().get("grandchild").setValue("modified grandchild");

        // Then - original should be unchanged
        assertEquals("original value", original.getChildren().get("child1").getValue());
        assertEquals("grandchild value",
            original.getChildren().get("child2").getChildren().get("grandchild").getValue());

        // And copy should have new values
        assertEquals("modified value", copy.getChildren().get("child1").getValue());
        assertEquals("modified grandchild",
            copy.getChildren().get("child2").getChildren().get("grandchild").getValue());
    }

    @Test
    void deepCopyPreservesStructure() {
        // Given - create a complex tree structure
        LinkedHashMap<String, PersonaNode> level3 = new LinkedHashMap<>();
        level3.put("leaf3a", PersonaNode.leaf("value3a"));
        level3.put("leaf3b", PersonaNode.leaf("value3b"));

        LinkedHashMap<String, PersonaNode> level2 = new LinkedHashMap<>();
        level2.put("leaf2", PersonaNode.leaf("value2"));
        level2.put("branch3", PersonaNode.branch(level3));

        LinkedHashMap<String, PersonaNode> level1 = new LinkedHashMap<>();
        level1.put("leaf1", PersonaNode.leaf("value1"));
        level1.put("branch2", PersonaNode.branch(level2));

        PersonaNode original = PersonaNode.branch(level1);

        // When
        PersonaNode copy = original.deepCopy();

        // Then - verify structure is preserved
        assertFalse(copy.isLeaf());
        assertEquals(2, copy.getChildren().size());

        assertTrue(copy.getChildren().get("leaf1").isLeaf());
        assertEquals("value1", copy.getChildren().get("leaf1").getValue());

        PersonaNode copyBranch2 = copy.getChildren().get("branch2");
        assertFalse(copyBranch2.isLeaf());
        assertEquals(2, copyBranch2.getChildren().size());

        assertTrue(copyBranch2.getChildren().get("leaf2").isLeaf());
        assertEquals("value2", copyBranch2.getChildren().get("leaf2").getValue());

        PersonaNode copyBranch3 = copyBranch2.getChildren().get("branch3");
        assertFalse(copyBranch3.isLeaf());
        assertEquals(2, copyBranch3.getChildren().size());

        assertTrue(copyBranch3.getChildren().get("leaf3a").isLeaf());
        assertEquals("value3a", copyBranch3.getChildren().get("leaf3a").getValue());

        assertTrue(copyBranch3.getChildren().get("leaf3b").isLeaf());
        assertEquals("value3b", copyBranch3.getChildren().get("leaf3b").getValue());
    }
}
