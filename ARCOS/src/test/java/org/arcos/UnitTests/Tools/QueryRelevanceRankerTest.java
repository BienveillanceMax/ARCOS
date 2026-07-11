package org.arcos.UnitTests.Tools;

import org.arcos.Tools.WebCommon.QueryRelevanceRanker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QueryRelevanceRankerTest {

    private final QueryRelevanceRanker ranker = new QueryRelevanceRanker();

    @Test
    @DisplayName("Given accented query, When blocks contain unaccented terms, Then they still match")
    void selectRelevant_ShouldMatchAccentInsensitive() {
        // Given
        List<String> blocks = List.of(
                "Un bloc qui parle de tout autre chose, sans rapport aucun avec le sujet.",
                "Les resultats de l'election presidentielle sont tombes hier soir tard.");

        // When
        String selected = ranker.selectRelevant("élection présidentielle", blocks, "fallback", 500);

        // Then
        assertThat(selected).contains("election presidentielle");
        assertThat(selected).doesNotContain("autre chose");
    }

    @Test
    @DisplayName("Given query with only stop-words overlap, When ranking, Then fallback text is returned")
    void selectRelevant_WithNoSignificantMatch_ShouldFallbackToFullText() {
        // Given
        List<String> blocks = List.of(
                "Un paragraphe qui ne contient aucun des termes recherchés par l'utilisateur.");

        // When
        String selected = ranker.selectRelevant("fusée spatiale réutilisable",
                blocks, "Début du texte complet de la page.", 500);

        // Then
        assertThat(selected).isEqualTo("Début du texte complet de la page.");
    }

    @Test
    @DisplayName("Given a char budget, When selecting, Then output stays within budget")
    void selectRelevant_ShouldRespectBudget() {
        // Given
        String block = "La fusée décolle demain matin depuis le pas de tir de Kourou en Guyane. ";
        List<String> blocks = List.of(block.repeat(3), block.repeat(3), block.repeat(3));

        // When
        String selected = ranker.selectRelevant("fusée Kourou", blocks, "fallback", 300);

        // Then
        assertThat(selected.length()).isLessThanOrEqualTo(300);
    }

    @Test
    @DisplayName("Given scattered relevant blocks, When selected, Then document order is preserved")
    void selectRelevant_ShouldPreserveDocumentOrder() {
        // Given
        List<String> blocks = List.of(
                "Premier passage sur la fusée et son lancement prévu la semaine prochaine.",
                "Un bloc de remplissage sans rapport, purement décoratif pour la page.",
                "Second passage sur la fusée avec des détails sur le lancement et la charge utile.");

        // When
        String selected = ranker.selectRelevant("fusée lancement", blocks, "fallback", 1000);

        // Then
        int first = selected.indexOf("Premier passage");
        int second = selected.indexOf("Second passage");
        assertThat(first).isGreaterThanOrEqualTo(0);
        assertThat(second).isGreaterThan(first);
    }

    @Test
    @DisplayName("Given oversized single best block, When selected, Then it is truncated to budget")
    void selectRelevant_WithOversizedBestBlock_ShouldTruncate() {
        // Given
        String huge = "fusée lancement ".repeat(100); // 1600 chars, seul bloc pertinent
        List<String> blocks = List.of(huge);

        // When
        String selected = ranker.selectRelevant("fusée lancement", blocks, "fallback", 200);

        // Then
        assertThat(selected).startsWith("fusée lancement");
        assertThat(selected).contains("[contenu tronqué]");
    }

    @Test
    @DisplayName("Given empty blocks, When selecting, Then fallback is truncated to budget")
    void selectRelevant_WithEmptyBlocks_ShouldReturnTruncatedFallback() {
        // Given
        String fallback = "x".repeat(500);

        // When
        String selected = ranker.selectRelevant("requête", List.of(), fallback, 100);

        // Then
        assertThat(selected).startsWith("xxx");
        assertThat(selected).contains("[contenu tronqué]");
    }
}
