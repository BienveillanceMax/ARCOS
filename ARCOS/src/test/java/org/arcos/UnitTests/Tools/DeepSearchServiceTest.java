package org.arcos.UnitTests.Tools;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.arcos.Tools.SearchTool.BraveSearchService.SearchResultItem;
import org.arcos.Tools.SearchTool.DeepSearchService;
import org.arcos.Tools.SearchTool.DeepSearchService.DeepSearchOutcome;
import org.arcos.Tools.WebCommon.ContentExtractor;
import org.arcos.Tools.WebCommon.ContentExtractor.PageContent;
import org.arcos.Tools.WebCommon.PageFetcher;
import org.arcos.Tools.WebCommon.PageFetcher.FetchedPage;
import org.arcos.Tools.WebCommon.QueryRelevanceRanker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeepSearchServiceTest {

    private static final int PAGES_TO_KEEP = 2;
    private static final int CANDIDATES = 3;
    private static final int PER_PAGE_CHARS = 1200;
    private static final int FETCH_TIMEOUT = 4;

    @Mock
    private PageFetcher pageFetcher;

    @Mock
    private ContentExtractor contentExtractor;

    @Mock
    private QueryRelevanceRanker ranker;

    private DeepSearchService deepSearchService;

    @BeforeEach
    void setUp() {
        deepSearchService = new DeepSearchService(pageFetcher, contentExtractor, ranker,
                PAGES_TO_KEEP, CANDIDATES, PER_PAGE_CHARS, FETCH_TIMEOUT);
    }

    private static SearchResultItem item(String url) {
        return new SearchResultItem("Titre " + url, url, "Description", null);
    }

    private void givenPageIsReadable(String url, String title) throws Exception {
        String html = "<html><head><title>" + title + "</title></head><body>...</body></html>";
        when(pageFetcher.fetch(eq(url), anyInt()))
                .thenReturn(new FetchedPage(url, "text/html", html));
        PageContent content = new PageContent(title,
                List.of("Un bloc de contenu pertinent et assez long pour la page " + title),
                "Texte complet de " + title);
        when(contentExtractor.extract(html)).thenReturn(content);
        lenient().when(contentExtractor.looksLikeSpa(eq(html), any(PageContent.class))).thenReturn(false);
        lenient().when(ranker.selectRelevant(anyString(), anyList(), anyString(), anyInt()))
                .thenReturn("Contenu pertinent de " + title);
    }

    @Test
    @DisplayName("Given 3 readable candidates, When enriched, Then only first 2 successes are kept in Brave order")
    void enrich_WithReadableCandidates_ShouldKeepFirstTwo() throws Exception {
        // Given
        givenPageIsReadable("https://a.com/1", "Page A");
        givenPageIsReadable("https://b.com/2", "Page B");

        // When
        DeepSearchOutcome outcome = deepSearchService.enrich("requête",
                List.of(item("https://a.com/1"), item("https://b.com/2"), item("https://c.com/3")));

        // Then
        assertThat(outcome.pages()).hasSize(2);
        assertThat(outcome.pages().get(0).title()).isEqualTo("Page A");
        assertThat(outcome.pages().get(1).title()).isEqualTo("Page B");
        assertThat(outcome.warnings()).isEmpty();
    }

    @Test
    @DisplayName("Given one candidate fails, When enriched, Then remaining pages are kept with a warning")
    void enrich_WithOneFailure_ShouldDegradeWithWarning() throws Exception {
        // Given
        when(pageFetcher.fetch(eq("https://a.com/1"), anyInt()))
                .thenThrow(new IOException("HTTP 403 pour https://a.com/1"));
        givenPageIsReadable("https://b.com/2", "Page B");
        givenPageIsReadable("https://c.com/3", "Page C");

        // When
        DeepSearchOutcome outcome = deepSearchService.enrich("requête",
                List.of(item("https://a.com/1"), item("https://b.com/2"), item("https://c.com/3")));

        // Then
        assertThat(outcome.pages()).extracting(DeepSearchService.DeepPage::title)
                .containsExactly("Page B", "Page C");
        assertThat(outcome.warnings()).hasSize(1);
        assertThat(outcome.warnings().get(0)).contains("Page non lue : https://a.com/1");
    }

    @Test
    @DisplayName("Given circuit breaker open on all fetches, When enriched, Then zero pages, warnings, no exception")
    void enrich_WhenCircuitBreakerOpen_ShouldReturnEmptyWithWarnings() throws Exception {
        // Given
        when(pageFetcher.fetch(anyString(), anyInt()))
                .thenThrow(CallNotPermittedException.createCallNotPermittedException(
                        CircuitBreaker.ofDefaults("webPage")));

        // When
        DeepSearchOutcome outcome = deepSearchService.enrich("requête",
                List.of(item("https://a.com/1"), item("https://b.com/2")));

        // Then
        assertThat(outcome.pages()).isEmpty();
        assertThat(outcome.warnings()).hasSize(2);
    }

    @Test
    @DisplayName("Given a SPA page, When enriched, Then it is skipped with a warning")
    void enrich_WithSpaPage_ShouldSkipWithWarning() throws Exception {
        // Given
        String html = "<html><body><div id='root'></div></body></html>";
        when(pageFetcher.fetch(eq("https://spa.com/app"), anyInt()))
                .thenReturn(new FetchedPage("https://spa.com/app", "text/html", html));
        when(contentExtractor.extract(html)).thenReturn(new PageContent("SPA", List.of(), ""));
        when(contentExtractor.looksLikeSpa(eq(html), any(PageContent.class))).thenReturn(true);

        // When
        DeepSearchOutcome outcome = deepSearchService.enrich("requête",
                List.of(item("https://spa.com/app")));

        // Then
        assertThat(outcome.pages()).isEmpty();
        assertThat(outcome.warnings()).hasSize(1);
        assertThat(outcome.warnings().get(0)).contains("JavaScript requis");
    }

    @Test
    @DisplayName("Given no fetchable candidates, When enriched, Then empty outcome without touching the fetcher")
    void enrich_WithNoCandidates_ShouldReturnEmpty() {
        // When
        DeepSearchOutcome outcome = deepSearchService.enrich("requête",
                List.of(item("https://www.youtube.com/watch?v=xyz"), item("https://a.com/doc.pdf")));

        // Then
        assertThat(outcome.pages()).isEmpty();
        verifyNoInteractions(pageFetcher);
    }

    // ── Filtre de candidats ──────────────────────────────────────────────────

    @Test
    @DisplayName("isFetchCandidate filters binary extensions and SPA/anti-bot hosts")
    void isFetchCandidate_ShouldFilterExtensionsAndHosts() {
        assertThat(DeepSearchService.isFetchCandidate("https://a.com/rapport.pdf")).isFalse();
        assertThat(DeepSearchService.isFetchCandidate("https://a.com/archive.zip")).isFalse();
        assertThat(DeepSearchService.isFetchCandidate("https://www.youtube.com/watch?v=1")).isFalse();
        assertThat(DeepSearchService.isFetchCandidate("https://x.com/user/status/1")).isFalse();
        assertThat(DeepSearchService.isFetchCandidate("https://fr.linkedin.com/in/someone")).isFalse();
        assertThat(DeepSearchService.isFetchCandidate(null)).isFalse();
        assertThat(DeepSearchService.isFetchCandidate("pas une url")).isFalse();

        assertThat(DeepSearchService.isFetchCandidate("https://fr.wikipedia.org/wiki/Fusée")).isTrue();
        assertThat(DeepSearchService.isFetchCandidate("https://www.lemonde.fr/article.html")).isTrue();
    }
}
