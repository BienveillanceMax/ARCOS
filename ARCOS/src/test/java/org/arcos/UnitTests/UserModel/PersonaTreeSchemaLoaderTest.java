package org.arcos.UnitTests.UserModel;

import org.arcos.UserModel.PersonaTree.PersonaNode;
import org.arcos.UserModel.PersonaTree.PersonaTree;
import org.arcos.UserModel.PersonaTree.PersonaTreeSchemaLoader;
import org.arcos.UserModel.UserModelProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PersonaTreeSchemaLoaderTest {

    private PersonaTreeSchemaLoader loader;

    @BeforeEach
    void setUp() {
        UserModelProperties properties = new UserModelProperties();
        properties.setPersonaTreeSchemaPath("persona-tree-schema.json");
        loader = new PersonaTreeSchemaLoader(properties);
        loader.init();
    }

    @Test
    void loadSchemaReturnsTreeWith5RootCategories() {
        // Given: loader initialized with schema
        // When
        PersonaTree tree = loader.loadSchema();

        // Then
        assertThat(tree).isNotNull();
        assertThat(tree.getRoots()).hasSize(5);
        assertThat(tree.getRoots()).containsKeys(
                "1_Biological_Characteristics",
                "2_Psychological_Characteristics",
                "3_Personality_Characteristics",
                "4_Identity_Characteristics",
                "5_Behavioral_Characteristics"
        );
    }

    @Test
    void validLeafPathsContains305Entries() {
        // Given: loader initialized with schema
        // When
        Set<String> leafPaths = loader.getValidLeafPaths();

        // Then
        assertThat(leafPaths).hasSize(305);
    }

    @Test
    void knownPathsAreValid() {
        // Given: loader initialized with schema
        // When/Then: known paths from the schema are valid
        assertThat(loader.isValidLeafPath("1_Biological_Characteristics.Physical_Appearance.Body_Build.Height")).isTrue();
        assertThat(loader.isValidLeafPath("1_Biological_Characteristics.Physical_Appearance.Body_Build.Weight")).isTrue();
        assertThat(loader.isValidLeafPath("1_Biological_Characteristics.Physical_Appearance.Vocal_Characteristics")).isTrue();
    }

    @Test
    void branchPathsAreNotValidLeafPaths() {
        // Given: loader initialized with schema
        // When/Then: branch paths are not leaf paths
        assertThat(loader.isValidLeafPath("1_Biological_Characteristics")).isFalse();
        assertThat(loader.isValidLeafPath("1_Biological_Characteristics.Physical_Appearance")).isFalse();
        assertThat(loader.isValidLeafPath("1_Biological_Characteristics.Physical_Appearance.Body_Build")).isFalse();
    }

    @Test
    void invalidPathsAreRejected() {
        // Given: loader initialized with schema
        // When/Then: invalid paths are rejected
        assertThat(loader.isValidLeafPath("Nonexistent.Path")).isFalse();
        assertThat(loader.isValidLeafPath("")).isFalse();
        assertThat(loader.isValidLeafPath(null)).isFalse();
        assertThat(loader.isValidLeafPath("1_Biological_Characteristics.Fake.Path.Here")).isFalse();
    }

    @Test
    void loadSchemaCreatesEmptyLeaves() {
        // Given: loader initialized with schema
        // When
        PersonaTree tree = loader.loadSchema();

        // Then: navigate to a known leaf and verify it's empty
        PersonaNode root = tree.getRoots().get("1_Biological_Characteristics");
        assertThat(root).isNotNull();
        assertThat(root.isLeaf()).isFalse();

        PersonaNode physicalAppearance = root.getChildren().get("Physical_Appearance");
        assertThat(physicalAppearance).isNotNull();
        assertThat(physicalAppearance.isLeaf()).isFalse();

        PersonaNode bodyBuild = physicalAppearance.getChildren().get("Body_Build");
        assertThat(bodyBuild).isNotNull();
        assertThat(bodyBuild.isLeaf()).isFalse();

        PersonaNode height = bodyBuild.getChildren().get("Height");
        assertThat(height).isNotNull();
        assertThat(height.isLeaf()).isTrue();
        assertThat(height.getValue()).isEmpty();

        PersonaNode vocalCharacteristics = physicalAppearance.getChildren().get("Vocal_Characteristics");
        assertThat(vocalCharacteristics).isNotNull();
        assertThat(vocalCharacteristics.isLeaf()).isTrue();
        assertThat(vocalCharacteristics.getValue()).isEmpty();
    }

    @Test
    void gracefulDegradationOnMissingSchema() {
        // Given: properties with nonexistent schema path
        UserModelProperties properties = new UserModelProperties();
        properties.setPersonaTreeSchemaPath("nonexistent-schema.json");
        PersonaTreeSchemaLoader badLoader = new PersonaTreeSchemaLoader(properties);

        // When
        badLoader.init();

        // Then: loader creates empty schema without throwing
        Set<String> leafPaths = badLoader.getValidLeafPaths();
        assertThat(leafPaths).isEmpty();

        PersonaTree tree = badLoader.loadSchema();
        assertThat(tree).isNotNull();
        assertThat(tree.getRoots()).isEmpty();
    }
}
