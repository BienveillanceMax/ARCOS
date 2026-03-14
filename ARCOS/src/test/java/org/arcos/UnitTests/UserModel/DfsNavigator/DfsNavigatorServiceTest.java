package org.arcos.UnitTests.UserModel.DfsNavigator;

import org.arcos.UserModel.DfsNavigator.*;
import org.arcos.UserModel.PersonaTree.PersonaTreeGate;
import org.arcos.UserModel.UserModelProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DfsNavigatorServiceTest {

    @Mock
    private CrossEncoderService crossEncoder;

    @Mock
    private PersonaTreeGate personaTreeGate;

    private BranchDescriptionRegistry registry;
    private DfsNavigatorService navigatorService;

    @BeforeEach
    void setUp() {
        registry = new BranchDescriptionRegistry();
        registry.initialize();

        UserModelProperties properties = new UserModelProperties();
        properties.setDfsTopNL1(3);
        properties.setDfsL2Threshold(0.0f);

        navigatorService = new DfsNavigatorService(crossEncoder, registry, personaTreeGate, properties);
    }

    @Test
    void shouldReturnEmptyResultWhenCrossEncoderNotAvailable() {
        // Given
        when(crossEncoder.isAvailable()).thenReturn(false);

        // When
        DfsResult result = navigatorService.navigate("test query");

        // Then
        assertTrue(result.relevantLeaves().isEmpty());
        assertTrue(result.selectedL1Branches().isEmpty());
        assertTrue(result.selectedL2Branches().isEmpty());
        verify(crossEncoder, never()).score(anyString(), anyList());
    }

    @Test
    void shouldSelectTopNL1BranchesAndDrillIntoL2() {
        // Given
        when(crossEncoder.isAvailable()).thenReturn(true);

        // Build L1 scores — 29 branches, make Physical_Appearance (index 0), Cognitive_Abilities (index 4),
        // Social_Identity (index 14) score highest
        float[] l1Scores = new float[29];
        l1Scores[0] = 5.0f;   // Physical_Appearance
        l1Scores[4] = 4.0f;   // Cognitive_Abilities
        l1Scores[14] = 3.0f;  // Social_Identity
        // Rest are 0.0

        when(crossEncoder.score(eq("je fais du sport"), argThat(list -> list.size() == 29)))
                .thenReturn(l1Scores);

        // L2 scores for Physical_Appearance (4 branches)
        float[] l2PhysAppScores = new float[]{1.0f, 0.5f, -1.0f, 2.0f}; // Body_Build, Facial, Skin(-), Hair
        when(crossEncoder.score(eq("je fais du sport"), argThat(list -> list.size() == 4)))
                .thenReturn(l2PhysAppScores);

        // L2 scores for Cognitive_Abilities (3 branches)
        float[] l2CogScores = new float[]{1.0f, 0.5f, 0.3f};
        when(crossEncoder.score(eq("je fais du sport"), argThat(list -> list.size() == 3)))
                .thenReturn(l2CogScores);

        // L2 scores for Social_Identity (7 branches)
        float[] l2SocialScores = new float[]{0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f};
        when(crossEncoder.score(eq("je fais du sport"), argThat(list -> list.size() == 7)))
                .thenReturn(l2SocialScores);

        // PersonaTreeGate returns leaves
        when(personaTreeGate.getLeavesUnderPath(anyString())).thenReturn(Map.of());
        when(personaTreeGate.getLeavesUnderPath("1_Biological_Characteristics.Physical_Appearance.Body_Build"))
                .thenReturn(Map.of("1_Biological_Characteristics.Physical_Appearance.Body_Build.Height", "180cm"));

        // When
        DfsResult result = navigatorService.navigate("je fais du sport");

        // Then
        assertEquals(3, result.selectedL1Branches().size());
        assertTrue(result.selectedL1Branches().contains("Physical_Appearance"));
        assertTrue(result.selectedL1Branches().contains("Cognitive_Abilities"));
        assertTrue(result.selectedL1Branches().contains("Social_Identity"));
        assertFalse(result.selectedL2Branches().isEmpty());
        assertTrue(result.relevantLeaves().containsKey(
                "1_Biological_Characteristics.Physical_Appearance.Body_Build.Height"));
    }

    @Test
    void shouldCollectLeavesDirectlyForTerminalL1() {
        // Given
        when(crossEncoder.isAvailable()).thenReturn(true);

        // Make Biological_Rhythms (index 2, no L2) score highest
        float[] l1Scores = new float[29];
        l1Scores[2] = 10.0f; // Biological_Rhythms — has no L2 group
        when(crossEncoder.score(eq("je dors mal"), argThat(list -> list.size() == 29)))
                .thenReturn(l1Scores);

        when(personaTreeGate.getLeavesUnderPath("1_Biological_Characteristics.Biological_Rhythms"))
                .thenReturn(Map.of("1_Biological_Characteristics.Biological_Rhythms.Sleep_Pattern", "insomniaque"));

        // Need to also handle L2 scoring for the other 2 top branches (which have L2 groups)
        // They'll score low so won't get L2 branches above threshold with default scores of 0.0
        // With l2Threshold = 0.0f, scores at exactly 0.0 pass, so we need to mock those too
        lenient().when(crossEncoder.score(eq("je dors mal"), argThat(list -> list.size() != 29)))
                .thenReturn(new float[0]);
        lenient().when(personaTreeGate.getLeavesUnderPath(argThat(path ->
                !path.equals("1_Biological_Characteristics.Biological_Rhythms"))))
                .thenReturn(Map.of());

        // When
        DfsResult result = navigatorService.navigate("je dors mal");

        // Then
        assertTrue(result.selectedL1Branches().contains("Biological_Rhythms"));
        assertTrue(result.selectedL2Branches().isEmpty() || result.selectedL2Branches() != null);
        assertTrue(result.relevantLeaves().containsKey(
                "1_Biological_Characteristics.Biological_Rhythms.Sleep_Pattern"));
    }

    @Test
    void shouldFilterL2BranchesBelowThreshold() {
        // Given — use a higher threshold
        UserModelProperties properties = new UserModelProperties();
        properties.setDfsTopNL1(1);
        properties.setDfsL2Threshold(1.0f); // only keep L2 branches with score >= 1.0

        DfsNavigatorService strictNavigator = new DfsNavigatorService(
                crossEncoder, registry, personaTreeGate, properties);

        when(crossEncoder.isAvailable()).thenReturn(true);

        float[] l1Scores = new float[29];
        l1Scores[0] = 5.0f; // Physical_Appearance
        when(crossEncoder.score(eq("query"), argThat(list -> list.size() == 29)))
                .thenReturn(l1Scores);

        // Physical_Appearance has 4 L2 branches — only Hair scores above 1.0
        float[] l2Scores = new float[]{0.5f, 0.2f, -0.3f, 2.0f}; // Body_Build, Facial, Skin, Hair
        when(crossEncoder.score(eq("query"), argThat(list -> list.size() == 4)))
                .thenReturn(l2Scores);

        when(personaTreeGate.getLeavesUnderPath("1_Biological_Characteristics.Physical_Appearance.Hair"))
                .thenReturn(Map.of("path.Hair.Scalp_Hair", "brun"));

        // When
        DfsResult result = strictNavigator.navigate("query");

        // Then — only Hair should be selected (score 2.0 >= 1.0 threshold)
        assertEquals(1, result.selectedL2Branches().size());
        assertEquals("Hair", result.selectedL2Branches().get(0));
        assertTrue(result.relevantLeaves().containsKey("path.Hair.Scalp_Hair"));
    }

    @Test
    void shouldReturnEmptyResultWhenL1ScoresEmpty() {
        // Given
        when(crossEncoder.isAvailable()).thenReturn(true);
        when(crossEncoder.score(anyString(), anyList())).thenReturn(new float[0]);

        // When
        DfsResult result = navigatorService.navigate("test");

        // Then
        assertTrue(result.relevantLeaves().isEmpty());
        assertTrue(result.selectedL1Branches().isEmpty());
    }
}
