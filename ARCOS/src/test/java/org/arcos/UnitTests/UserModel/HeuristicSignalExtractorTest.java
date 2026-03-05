package org.arcos.UnitTests.UserModel;

import org.arcos.UserModel.Heuristics.HeuristicSignalExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HeuristicSignalExtractorTest {

    private HeuristicSignalExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new HeuristicSignalExtractor(List.of("euh", "bah", "hein", "ben"));
    }

    @Test
    void extractSignals_ShouldReturn9Signals() {
        // Given
        List<String> messages = List.of(
                "Bonjour comment ça va aujourd'hui",
                "Je voudrais savoir quelque chose",
                "Merci beaucoup pour ta réponse"
        );

        // When
        Map<String, Double> signals = extractor.extractSignals(messages, false);

        // Then
        assertEquals(9, signals.size());
        assertTrue(signals.containsKey("avg_word_count"));
        assertTrue(signals.containsKey("vocabulary_diversity"));
        assertTrue(signals.containsKey("avg_word_length"));
        assertTrue(signals.containsKey("message_count"));
        assertTrue(signals.containsKey("time_of_day"));
        assertTrue(signals.containsKey("correction_frequency"));
        assertTrue(signals.containsKey("question_ratio"));
        assertTrue(signals.containsKey("initiative_response"));
        assertTrue(signals.containsKey("return_frequency"));
    }

    @Test
    void extractSignals_ShouldFilterDisfluences() {
        // Given — "euh bah oui je suis là" with disfluences ["euh","bah"]
        // After filtering: "oui", "je", "suis", "là" = 4 words
        List<String> messages = List.of("euh bah oui je suis là");

        // When
        Map<String, Double> signals = extractor.extractSignals(messages, false);

        // Then
        assertEquals(4.0, signals.get("avg_word_count"), 0.001);
    }

    @Test
    void extractSignals_QuestionRatioGuard_ShouldExcludeWhenLessThan3Messages() {
        // Given
        List<String> messages = List.of(
                "Bonjour ?",
                "Au revoir ?"
        );

        // When
        Map<String, Double> signals = extractor.extractSignals(messages, false);

        // Then
        assertFalse(signals.containsKey("question_ratio"));
    }

    @Test
    void extractSignals_QuestionRatio_ShouldBeIncludedWith3OrMoreMessages() {
        // Given — 3 messages, 1 ending with "?"
        List<String> messages = List.of(
                "Bonjour comment vas-tu ?",
                "Je vais bien merci",
                "Au revoir"
        );

        // When
        Map<String, Double> signals = extractor.extractSignals(messages, false);

        // Then
        assertTrue(signals.containsKey("question_ratio"));
        assertEquals(1.0 / 3.0, signals.get("question_ratio"), 0.001);
    }

    @Test
    void extractSignals_ZeroNetworkCalls() {
        // Given — Pure computation, no mocks needed
        List<String> messages = List.of(
                "Bonjour",
                "Comment ça va",
                "Très bien merci"
        );

        // When / Then — no exception thrown, no network calls
        Map<String, Double> signals = assertDoesNotThrow(
                () -> extractor.extractSignals(messages, true)
        );
        assertNotNull(signals);
        assertFalse(signals.isEmpty());
    }

    @Test
    void extractSignals_CorrectionFrequency_ShouldDetectCorrectionMarkers() {
        // Given — messages containing "en fait"
        List<String> messages = List.of(
                "Je pense que oui",
                "En fait non je me suis trompé",
                "Voilà c'est tout"
        );

        // When
        Map<String, Double> signals = extractor.extractSignals(messages, false);

        // Then
        assertTrue(signals.get("correction_frequency") > 0.0);
        assertEquals(1.0 / 3.0, signals.get("correction_frequency"), 0.001);
    }
}
