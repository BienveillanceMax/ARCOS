package org.arcos.UnitTests.Tools;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.arcos.Exceptions.SearchException;
import org.arcos.IO.OuputHandling.StateHandler.CentralFeedBackHandler;
import org.arcos.Tools.Actions.ActionResult;
import org.arcos.Tools.Actions.SearchActions;
import org.arcos.Tools.SearchTool.BraveSearchService;
import org.arcos.Tools.SearchTool.BraveSearchService.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests pour Chercher_sur_Internet (SearchActions + BraveSearchService).
 *
 * Valide :
 * - AC1 : recherche avec réponse Brave => ActionResult avec résultats
 * - AC6 : service indisponible => dégradation gracieuse sans crash
 */
@ExtendWith(MockitoExtension.class)
class SearchToolTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // SearchActions — action layer
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("SearchActions — Chercher_sur_Internet")
    class SearchActionsTests {

        @Mock
        private BraveSearchService searchService;

        @Mock
        private CentralFeedBackHandler centralFeedBackHandler;

        private SearchActions searchActions;

        @BeforeEach
        void setUp() {
            searchActions = new SearchActions(searchService, centralFeedBackHandler, 5, "FR", "fr");
        }

        // ── AC1 : normal operation ──────────────────────────────────────────

        @Test
        @DisplayName("Given a query, When Brave returns results, Then ActionResult contains formatted data")
        void searchTheWeb_WithResults_ShouldReturnFormattedActionResult() throws SearchException {
            // Given
            when(searchService.isAvailable()).thenReturn(true);

            SearchResultItem item1 = new SearchResultItem(
                    "IA et emploi en 2026",
                    "https://lemonde.fr/ia-emploi",
                    "L'intelligence artificielle transforme le marché du travail.",
                    "2026-03-20"
            );
            SearchResultItem item2 = new SearchResultItem(
                    "Régulation de l'IA en Europe",
                    "https://euronews.com/ia-regulation",
                    "L'UE adopte un nouveau cadre réglementaire.",
                    null
            );

            SearchResult searchResult = new SearchResult("IA emploi", List.of(item1, item2), 42);
            when(searchService.search(anyString(), any(SearchOptions.class))).thenReturn(searchResult);

            // When
            ActionResult result = searchActions.searchTheWeb("IA emploi");

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).isEqualTo("Recherche effectuée avec succès");
            assertThat((List<?>) result.getData()).hasSize(2);

            // Verify formatted content includes title, description, URL
            List<String> data = (List<String>) result.getData();
            assertThat(data.get(0)).contains("IA et emploi en 2026");
            assertThat(data.get(0)).contains("lemonde.fr/ia-emploi");
            assertThat(data.get(0)).contains("2026-03-20");  // date present
            assertThat(data.get(1)).contains("Régulation de l'IA en Europe");

            // Metadata and execution time
            assertThat(result.getMetadata()).containsEntry("query", "IA emploi");
            assertThat(result.getExecutionTimeMs()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("Given a query, When Brave returns single result, Then that result is returned without 'aucun résultat'")
        void searchTheWeb_WithSingleResult_ShouldReturnThatResult() throws SearchException {
            // Given
            when(searchService.isAvailable()).thenReturn(true);
            SearchResultItem item = new SearchResultItem("Single", "https://a.com", "Desc", null);
            SearchResult searchResult = new SearchResult("rare query", List.of(item), 1);
            when(searchService.search(anyString(), any(SearchOptions.class))).thenReturn(searchResult);

            // When
            ActionResult result = searchActions.searchTheWeb("rare query");

            // Then
            assertThat(result.isSuccess()).isTrue();
            List<String> data = (List<String>) result.getData();
            assertThat(data).hasSize(1);
            assertThat(data.get(0)).contains("Single");
            assertThat(data).noneMatch(d -> d.contains("Aucun résultat pertinent"));
        }

        @Test
        @DisplayName("Given a query, When Brave returns empty results, Then 'aucun résultat' is present")
        void searchTheWeb_WithNoResults_ShouldReturnNoResultMessage() throws Exception {
            // Given
            when(searchService.isAvailable()).thenReturn(true);
            SearchResult emptyResult = new SearchResult("unknown", List.of(), 0);
            when(searchService.search(anyString(), any(SearchOptions.class))).thenReturn(emptyResult);

            // When
            ActionResult result = searchActions.searchTheWeb("unknown");

            // Then
            assertThat(result.isSuccess()).isTrue();
            List<String> data = (List<String>) result.getData();
            assertThat(data).anyMatch(d -> d.contains("Aucun résultat pertinent"));
        }

        // ── AC6 : graceful degradation ──────────────────────────────────────

        @Test
        @DisplayName("Given Brave API key missing, When search called, Then failure with explicit message")
        void searchTheWeb_WhenApiKeyMissing_ShouldReturnFailure() throws Exception {
            // Given
            when(searchService.isAvailable()).thenReturn(false);

            // When
            ActionResult result = searchActions.searchTheWeb("test query");

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("BRAVE_SEARCH_API_KEY");
            assertThat(result.getExecutionTimeMs()).isEqualTo(0);
            verify(searchService, never()).search(anyString(), any());
        }

        @Test
        @DisplayName("Given Brave API throws SearchException, When search called, Then failure ActionResult is returned")
        void searchTheWeb_WhenSearchException_ShouldReturnFailure() throws SearchException {
            // Given
            when(searchService.isAvailable()).thenReturn(true);
            when(searchService.search(anyString(), any(SearchOptions.class)))
                    .thenThrow(new SearchException("API rate limit exceeded"));

            // When
            ActionResult result = searchActions.searchTheWeb("test");

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("Erreur de recherche");
            assertThat(result.getMessage()).contains("API rate limit exceeded");
        }

        @Test
        @DisplayName("Given circuit breaker open, When search called, Then failure with 'temporairement indisponible'")
        void searchTheWeb_WhenCircuitBreakerOpen_ShouldReturnFailure() throws SearchException {
            // Given
            when(searchService.isAvailable()).thenReturn(true);
            when(searchService.search(anyString(), any(SearchOptions.class)))
                    .thenThrow(CallNotPermittedException.createCallNotPermittedException(
                            CircuitBreaker.ofDefaults("braveSearch")));

            // When
            ActionResult result = searchActions.searchTheWeb("test");

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("temporairement indisponible");
        }

        @Test
        @DisplayName("Given locale defaults, When searching, Then options carry country=FR and language=fr")
        void searchTheWeb_ShouldApplyLocaleDefaults() throws SearchException {
            // Given
            when(searchService.isAvailable()).thenReturn(true);
            when(searchService.search(anyString(), any(SearchOptions.class)))
                    .thenReturn(new SearchResult("q", List.of(), 0));

            // When
            searchActions.searchTheWeb("q");

            // Then
            ArgumentCaptor<SearchOptions> captor = ArgumentCaptor.forClass(SearchOptions.class);
            verify(searchService).search(anyString(), captor.capture());
            assertThat(captor.getValue().getCountry()).isEqualTo("FR");
            assertThat(captor.getValue().getLanguage()).isEqualTo("fr");
        }

        @Test
        @DisplayName("Given search succeeds, Then feedback handler is invoked for LONGTASK start and end")
        void searchTheWeb_ShouldSendFeedbackEvents() throws SearchException {
            // Given
            when(searchService.isAvailable()).thenReturn(true);
            SearchResult result = new SearchResult("q", List.of(), 0);
            when(searchService.search(anyString(), any(SearchOptions.class))).thenReturn(result);

            // When
            searchActions.searchTheWeb("q");

            // Then
            verify(centralFeedBackHandler, times(2)).handleFeedBack(any());
        }
    }
}
