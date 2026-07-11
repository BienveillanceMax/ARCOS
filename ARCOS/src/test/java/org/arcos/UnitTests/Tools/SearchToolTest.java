package org.arcos.UnitTests.Tools;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.arcos.Exceptions.SearchException;
import org.arcos.IO.OuputHandling.StateHandler.CentralFeedBackHandler;
import org.arcos.Tools.Actions.ActionResult;
import org.arcos.Tools.Actions.SearchActions;
import org.arcos.Tools.SearchTool.BraveSearchService;
import org.arcos.Tools.SearchTool.BraveSearchService.*;
import org.arcos.Tools.SearchTool.DeepSearchService;
import org.arcos.Tools.SearchTool.DeepSearchService.DeepPage;
import org.arcos.Tools.SearchTool.DeepSearchService.DeepSearchOutcome;
import org.arcos.Tools.SearchTool.SearchResultFormatter;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests pour Chercher_sur_Internet (SearchActions + formatage réel).
 *
 * Valide :
 * - AC1 : recherche avec réponse Brave => ActionResult avec résultats
 * - Deep search : mode approfondi par défaut, mode rapide sur demande
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
        private DeepSearchService deepSearchService;

        @Mock
        private CentralFeedBackHandler centralFeedBackHandler;

        private SearchActions searchActions;

        @BeforeEach
        void setUp() {
            searchActions = new SearchActions(searchService, deepSearchService,
                    new SearchResultFormatter(), centralFeedBackHandler,
                    5, "FR", "fr", true, 6000);
        }

        private void givenSearchReturns(SearchResultItem... items) throws SearchException {
            when(searchService.isAvailable()).thenReturn(true);
            SearchResult searchResult = new SearchResult("q", List.of(items), items.length);
            when(searchService.search(anyString(), any(SearchOptions.class))).thenReturn(searchResult);
        }

        private void givenNoDeepPages() {
            when(deepSearchService.enrich(anyString(), anyList()))
                    .thenReturn(new DeepSearchOutcome(List.of(), List.of()));
        }

        // ── AC1 : normal operation ──────────────────────────────────────────

        @Test
        @DisplayName("Given a query, When Brave returns results, Then ActionResult contains formatted data")
        void searchTheWeb_WithResults_ShouldReturnFormattedActionResult() throws SearchException {
            // Given
            givenSearchReturns(
                    new SearchResultItem("IA et emploi en 2026", "https://lemonde.fr/ia-emploi",
                            "L'intelligence artificielle transforme le marché du travail.", "2026-03-20"),
                    new SearchResultItem("Régulation de l'IA en Europe", "https://euronews.com/ia-regulation",
                            "L'UE adopte un nouveau cadre réglementaire.", null));
            givenNoDeepPages();

            // When
            ActionResult result = searchActions.searchTheWeb("IA emploi", null, null, null);

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).isEqualTo("Recherche effectuée avec succès");

            List<String> data = (List<String>) result.getData();
            assertThat(data.get(0)).contains("2 résultats");
            assertThat(String.join("\n", data)).contains("IA et emploi en 2026");
            assertThat(String.join("\n", data)).contains("lemonde.fr/ia-emploi");
            assertThat(String.join("\n", data)).contains("2026-03-20");
            assertThat(String.join("\n", data)).contains("Régulation de l'IA en Europe");

            // Metadata and execution time
            assertThat(result.getMetadata()).containsEntry("query", "IA emploi");
            assertThat(result.getExecutionTimeMs()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("Given a query, When Brave returns single result, Then that result is returned without 'aucun résultat'")
        void searchTheWeb_WithSingleResult_ShouldReturnThatResult() throws SearchException {
            // Given
            givenSearchReturns(new SearchResultItem("Single", "https://a.com", "Desc", null));
            givenNoDeepPages();

            // When
            ActionResult result = searchActions.searchTheWeb("rare query", null, null, null);

            // Then
            assertThat(result.isSuccess()).isTrue();
            List<String> data = (List<String>) result.getData();
            assertThat(String.join("\n", data)).contains("Single");
            assertThat(data).noneMatch(d -> d.contains("Aucun résultat"));
        }

        @Test
        @DisplayName("Given a query, When Brave returns empty results, Then 'aucun résultat' is present")
        void searchTheWeb_WithNoResults_ShouldReturnNoResultMessage() throws Exception {
            // Given
            givenSearchReturns();

            // When
            ActionResult result = searchActions.searchTheWeb("unknown", null, null, null);

            // Then
            assertThat(result.isSuccess()).isTrue();
            List<String> data = (List<String>) result.getData();
            assertThat(data).anyMatch(d -> d.contains("Aucun résultat trouvé"));
            verify(deepSearchService, never()).enrich(anyString(), anyList());
        }

        // ── Deep search ─────────────────────────────────────────────────────

        @Test
        @DisplayName("Given default mode, When searching, Then deep pages content appears in data")
        void searchTheWeb_DefaultMode_ShouldIncludeDeepContent() throws SearchException {
            // Given
            givenSearchReturns(new SearchResultItem("Titre", "https://a.com/article", "Desc", null));
            when(deepSearchService.enrich(anyString(), anyList())).thenReturn(new DeepSearchOutcome(
                    List.of(new DeepPage("https://a.com/article", "Titre de la page",
                            "Le contenu pertinent extrait de la page.")),
                    List.of()));

            // When
            ActionResult result = searchActions.searchTheWeb("ma recherche", null, null, null);

            // Then
            List<String> data = (List<String>) result.getData();
            assertThat(String.join("\n", data)).contains("Contenu de : Titre de la page");
            assertThat(String.join("\n", data)).contains("contenu pertinent extrait");
            assertThat(result.getMetadata()).containsEntry("mode", "approfondi");
            assertThat(result.getMetadata()).containsEntry("pages_lues", 1);
        }

        @Test
        @DisplayName("Given mode 'rapide', When searching, Then deep search is never invoked")
        void searchTheWeb_RapideMode_ShouldSkipDeepSearch() throws SearchException {
            // Given
            givenSearchReturns(new SearchResultItem("Titre", "https://a.com", "Desc", null));

            // When
            ActionResult result = searchActions.searchTheWeb("ma recherche", null, "rapide", null);

            // Then
            verify(deepSearchService, never()).enrich(anyString(), anyList());
            assertThat(result.getMetadata()).containsEntry("mode", "rapide");
            assertThat(result.getMetadata()).containsEntry("pages_lues", 0);
        }

        @Test
        @DisplayName("Given deep pages fail, When searching, Then warnings are propagated and snippets remain")
        void searchTheWeb_WhenDeepFails_ShouldDegradeToSnippetsWithWarnings() throws SearchException {
            // Given
            givenSearchReturns(new SearchResultItem("Titre", "https://a.com", "Desc", null));
            when(deepSearchService.enrich(anyString(), anyList())).thenReturn(new DeepSearchOutcome(
                    List.of(), List.of("Page non lue : https://a.com (délai dépassé)")));

            // When
            ActionResult result = searchActions.searchTheWeb("ma recherche", null, null, null);

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getWarnings()).anyMatch(w -> w.contains("Page non lue"));
            assertThat(String.join("\n", (List<String>) result.getData())).contains("Titre");
        }

        @Test
        @DisplayName("Tool output must never contain markdown bold markers (TTS)")
        void searchTheWeb_ShouldNeverEmitMarkdown() throws SearchException {
            // Given
            givenSearchReturns(new SearchResultItem("Titre", "https://a.com", "Desc", "2026-01-01"));
            when(deepSearchService.enrich(anyString(), anyList())).thenReturn(new DeepSearchOutcome(
                    List.of(new DeepPage("https://a.com", "Titre", "Contenu.")), List.of()));

            // When
            ActionResult result = searchActions.searchTheWeb("ma recherche", null, null, null);

            // Then
            List<String> data = (List<String>) result.getData();
            assertThat(data).noneMatch(d -> d.contains("**"));
        }

        // ── Paramètres fraicheur / page / locale ────────────────────────────

        @Test
        @DisplayName("Given fraicheur 'semaine', When searching, Then options carry PAST_WEEK")
        void searchTheWeb_WithFraicheurSemaine_ShouldMapToPastWeek() throws SearchException {
            // Given
            givenSearchReturns();

            // When
            searchActions.searchTheWeb("q", "semaine", "rapide", null);

            // Then
            ArgumentCaptor<SearchOptions> captor = ArgumentCaptor.forClass(SearchOptions.class);
            verify(searchService).search(anyString(), captor.capture());
            assertThat(captor.getValue().getFreshness()).isEqualTo(Freshness.PAST_WEEK);
        }

        @Test
        @DisplayName("Given unknown fraicheur, When searching, Then ALL freshness plus warning")
        void searchTheWeb_WithUnknownFraicheur_ShouldFallbackToAllWithWarning() throws SearchException {
            // Given
            givenSearchReturns();

            // When
            ActionResult result = searchActions.searchTheWeb("q", "hier soir tard", "rapide", null);

            // Then
            ArgumentCaptor<SearchOptions> captor = ArgumentCaptor.forClass(SearchOptions.class);
            verify(searchService).search(anyString(), captor.capture());
            assertThat(captor.getValue().getFreshness()).isEqualTo(Freshness.ALL);
            assertThat(result.getWarnings()).anyMatch(w -> w.contains("Fraîcheur inconnue"));
        }

        @Test
        @DisplayName("Given page=2, When searching, Then Brave offset is 1 (page index, not result index)")
        void searchTheWeb_WithPage2_ShouldSetOffset1() throws SearchException {
            // Given
            givenSearchReturns();

            // When
            searchActions.searchTheWeb("q", null, "rapide", 2);

            // Then
            ArgumentCaptor<SearchOptions> captor = ArgumentCaptor.forClass(SearchOptions.class);
            verify(searchService).search(anyString(), captor.capture());
            assertThat(captor.getValue().getOffset()).isEqualTo(1);
        }

        @Test
        @DisplayName("Given locale defaults, When searching, Then options carry country=FR and language=fr")
        void searchTheWeb_ShouldApplyLocaleDefaults() throws SearchException {
            // Given
            givenSearchReturns();

            // When
            searchActions.searchTheWeb("q", null, null, null);

            // Then
            ArgumentCaptor<SearchOptions> captor = ArgumentCaptor.forClass(SearchOptions.class);
            verify(searchService).search(anyString(), captor.capture());
            assertThat(captor.getValue().getCountry()).isEqualTo("FR");
            assertThat(captor.getValue().getLanguage()).isEqualTo("fr");
        }

        // ── AC6 : graceful degradation ──────────────────────────────────────

        @Test
        @DisplayName("Given Brave API key missing, When search called, Then failure with explicit message")
        void searchTheWeb_WhenApiKeyMissing_ShouldReturnFailure() throws Exception {
            // Given
            when(searchService.isAvailable()).thenReturn(false);

            // When
            ActionResult result = searchActions.searchTheWeb("test query", null, null, null);

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
            ActionResult result = searchActions.searchTheWeb("test", null, null, null);

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
            ActionResult result = searchActions.searchTheWeb("test", null, null, null);

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("temporairement indisponible");
        }

        @Test
        @DisplayName("Given search succeeds, Then feedback handler is invoked for LONGTASK start and end")
        void searchTheWeb_ShouldSendFeedbackEvents() throws SearchException {
            // Given
            givenSearchReturns();

            // When
            searchActions.searchTheWeb("q", null, null, null);

            // Then
            verify(centralFeedBackHandler, times(2)).handleFeedBack(any());
        }
    }
}
