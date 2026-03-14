package org.arcos.UnitTests.UserModel.DfsNavigator;

import org.arcos.UserModel.DfsNavigator.BranchDescriptionRegistry;
import org.arcos.UserModel.DfsNavigator.L2Group;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BranchDescriptionRegistryTest {

    private BranchDescriptionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new BranchDescriptionRegistry();
        registry.initialize();
    }

    @Test
    void shouldLoad29L1Branches() {
        // Given/When
        var branches = registry.getL1Branches();

        // Then
        assertEquals(29, branches.size());
        assertEquals(29, registry.getL1Descriptions().size());
        assertEquals(29, registry.getL1Keys().size());
    }

    @Test
    void shouldReturnL2GroupForPhysicalAppearance() {
        // Given/When
        Optional<L2Group> group = registry.getL2GroupForParent("Physical_Appearance");

        // Then
        assertTrue(group.isPresent());
        assertEquals(4, group.get().branches().size());
        assertEquals("Physical_Appearance", group.get().parentKey());
        assertEquals("Body_Build", group.get().branches().get(0).key());
    }

    @Test
    void shouldReturnEmptyL2GroupForBiologicalRhythms() {
        // Given/When
        Optional<L2Group> group = registry.getL2GroupForParent("Biological_Rhythms");

        // Then
        assertTrue(group.isEmpty());
    }

    @Test
    void shouldReturnCorrectTreePathForL1() {
        // Given/When
        String treePath = registry.getTreePathForL1("Physical_Appearance");

        // Then
        assertEquals("1_Biological_Characteristics.Physical_Appearance", treePath);
    }

    @Test
    void shouldReturnCorrectTreePathForL1SocialIdentity() {
        // Given/When
        String treePath = registry.getTreePathForL1("Social_Identity");

        // Then
        assertEquals("4_Identity_Characteristics.Social_Identity", treePath);
    }

    @Test
    void shouldReturnCorrectTreePathForL2() {
        // Given/When
        String treePath = registry.getTreePathForL2("Physical_Appearance", "Hair");

        // Then
        assertEquals("1_Biological_Characteristics.Physical_Appearance.Hair", treePath);
    }

    @Test
    void shouldThrowForUnknownL1Key() {
        // Given/When/Then
        assertThrows(IllegalArgumentException.class, () -> registry.getTreePathForL1("Unknown_Branch"));
    }

    @Test
    void shouldLoadAllL2Groups() {
        // Given/When — there are 16 L2 groups in the JSON

        // Then — verify some specific groups exist
        assertTrue(registry.getL2GroupForParent("Physiological_Status").isPresent());
        assertEquals(6, registry.getL2GroupForParent("Physiological_Status").get().branches().size());

        assertTrue(registry.getL2GroupForParent("Cognitive_Abilities").isPresent());
        assertEquals(3, registry.getL2GroupForParent("Cognitive_Abilities").get().branches().size());

        assertTrue(registry.getL2GroupForParent("Social_Identity").isPresent());
        assertEquals(7, registry.getL2GroupForParent("Social_Identity").get().branches().size());
    }
}
