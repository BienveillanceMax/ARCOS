package org.arcos.UnitTests.UserModel.DfsNavigator;

import org.arcos.UserModel.DfsNavigator.BranchDescriptionRegistry;
import org.arcos.UserModel.DfsNavigator.UserContextFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UserContextFormatterTest {

    private UserContextFormatter formatter;

    @BeforeEach
    void setUp() {
        BranchDescriptionRegistry registry = new BranchDescriptionRegistry();
        registry.initialize();
        formatter = new UserContextFormatter(registry);
    }

    @Test
    void shouldFormatLeavesAsMarkdown() {
        // Given
        Map<String, String> leaves = new LinkedHashMap<>();
        leaves.put("1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair", "brun, courts");
        leaves.put("4_Identity_Characteristics.Social_Identity.Occupational_Role.Job_Title", "ingenieur");

        // When
        String result = formatter.format(leaves);

        // Then
        assertTrue(result.startsWith("## Profil utilisateur\n"));
        assertTrue(result.contains("- Apparence physique > Cheveux et pilosit"));
        assertTrue(result.contains(" : brun, courts\n"));
        assertTrue(result.contains("- Identit"));
        assertTrue(result.contains(" : ingenieur\n"));
    }

    @Test
    void shouldReturnEmptyStringForEmptyLeaves() {
        // Given/When
        String result = formatter.format(Map.of());

        // Then
        assertEquals("", result);
    }

    @Test
    void shouldReturnEmptyStringForNull() {
        // Given/When
        String result = formatter.format(null);

        // Then
        assertEquals("", result);
    }

    @Test
    void shouldTranslateL1PathOnly() {
        // Given — path with only root + L1 (2 segments)
        String path = "1_Biological_Characteristics.Physical_Appearance";

        // When
        String readable = formatter.humanReadablePath(path);

        // Then
        assertEquals("Apparence physique", readable);
    }

    @Test
    void shouldTranslateL1PlusL2Path() {
        // Given
        String path = "1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair";

        // When
        String readable = formatter.humanReadablePath(path);

        // Then — L1 short = "Apparence physique", L2 short = "Cheveux et pilosite" (before colon)
        assertTrue(readable.startsWith("Apparence physique > Cheveux"));
    }

    @Test
    void shouldHandleL1WithoutL2GroupGracefully() {
        // Given — Biological_Rhythms has no L2 group, so third segment is just a leaf name
        String path = "1_Biological_Characteristics.Biological_Rhythms.Sleep_Pattern";

        // When
        String readable = formatter.humanReadablePath(path);

        // Then — L1 description used, third segment falls back to underscore replacement
        assertTrue(readable.startsWith("Rythmes biologiques"));
        assertTrue(readable.contains("Sleep Pattern"));
    }

    @Test
    void shouldHandleSingleSegmentPath() {
        // Given
        String path = "something";

        // When
        String readable = formatter.humanReadablePath(path);

        // Then
        assertEquals("something", readable);
    }
}
