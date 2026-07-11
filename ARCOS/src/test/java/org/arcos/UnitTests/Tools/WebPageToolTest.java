package org.arcos.UnitTests.Tools;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.arcos.IO.OuputHandling.StateHandler.CentralFeedBackHandler;
import org.arcos.Tools.Actions.ActionResult;
import org.arcos.Tools.Actions.WebPageActions;
import org.arcos.Tools.WebCommon.ContentExtractor;
import org.arcos.Tools.WebCommon.PageFetcher;
import org.arcos.Tools.WebPageTool.WebPageService;
import org.arcos.Tools.WebPageTool.WebPageService.PageSlice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Tests pour Lire_une_page_web (WebPageActions + WebPageService).
 *
 * Valide :
 * - AC3 : lecture de page web => titre + contenu par parties (pagination)
 * - AC6 : service indisponible (timeout, IOException) => degradation gracieuse
 */
@ExtendWith(MockitoExtension.class)
class WebPageToolTest {

    private static final int MAX_CONTENT_LENGTH = 4000;
    private static final int MAX_TOTAL_CHARS = 40000;
    private static final int TIMEOUT_SECONDS = 15;

    // ═══════════════════════════════════════════════════════════════════════════
    // WebPageActions — action layer (WebPageService mocké)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Lire_une_page_web — action layer")
    class ActionLayerTests {

        @Mock
        private WebPageService webPageService;

        @Mock
        private CentralFeedBackHandler centralFeedBackHandler;

        private WebPageActions webPageActions;

        @BeforeEach
        void setUp() {
            webPageActions = new WebPageActions(webPageService, centralFeedBackHandler,
                    MAX_CONTENT_LENGTH, MAX_TOTAL_CHARS, TIMEOUT_SECONDS);
        }

        @Test
        @DisplayName("Given valid URL, When page is fetched, Then title and content are returned")
        void readWebPage_WithValidUrl_ShouldReturnTitleAndContent() throws Exception {
            // Given
            String url = "https://www.lemonde.fr/article-important";
            when(webPageService.fetchPage(url, 1, MAX_CONTENT_LENGTH, MAX_TOTAL_CHARS, TIMEOUT_SECONDS))
                    .thenReturn(new PageSlice("Article important",
                            "Voici le contenu principal de l'article.", 1, 1));

            // When
            ActionResult result = webPageActions.readWebPage(url, null);

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).isEqualTo("Page lue avec succès");
            List<String> data = (List<String>) result.getData();
            assertThat(data.get(0)).isEqualTo("Titre : Article important — Partie 1/1");
            assertThat(data.get(1)).contains("contenu principal");
            assertThat(data).noneMatch(d -> d.contains("Suite disponible"));
            assertThat(result.getMetadata()).containsEntry("url", url);
            assertThat(result.getMetadata()).containsEntry("titre", "Article important");
            assertThat(result.getMetadata()).containsEntry("partie", "1/1");
        }

        @Test
        @DisplayName("Given a long page, When part 1 is read, Then continuation hint is present")
        void readWebPage_WithLongPage_ShouldAnnounceContinuation() throws Exception {
            // Given
            String url = "https://www.example.com/long-article";
            when(webPageService.fetchPage(url, 1, MAX_CONTENT_LENGTH, MAX_TOTAL_CHARS, TIMEOUT_SECONDS))
                    .thenReturn(new PageSlice("Long article", "Première partie du contenu.", 1, 3));

            // When
            ActionResult result = webPageActions.readWebPage(url, null);

            // Then
            List<String> data = (List<String>) result.getData();
            assertThat(data.get(0)).contains("Partie 1/3");
            assertThat(data.get(2)).contains("Suite disponible");
            assertThat(data.get(2)).contains("page=2");
        }

        @Test
        @DisplayName("Given page=2 requested, When read, Then service receives part 2")
        void readWebPage_WithPage2_ShouldRequestPart2() throws Exception {
            // Given
            String url = "https://www.example.com/long-article";
            when(webPageService.fetchPage(url, 2, MAX_CONTENT_LENGTH, MAX_TOTAL_CHARS, TIMEOUT_SECONDS))
                    .thenReturn(new PageSlice("Long article", "Deuxième partie.", 2, 3));

            // When
            ActionResult result = webPageActions.readWebPage(url, 2);

            // Then
            verify(webPageService).fetchPage(url, 2, MAX_CONTENT_LENGTH, MAX_TOTAL_CHARS, TIMEOUT_SECONDS);
            assertThat(result.getMetadata()).containsEntry("partie", "2/3");
        }

        @Test
        @DisplayName("Given null or negative page, When read, Then defaults to part 1")
        void readWebPage_WithInvalidPageNumber_ShouldDefaultToPart1() throws Exception {
            // Given
            String url = "https://example.com/page";
            when(webPageService.fetchPage(eq(url), eq(1), anyInt(), anyInt(), anyInt()))
                    .thenReturn(new PageSlice("T", "Contenu.", 1, 1));

            // When
            webPageActions.readWebPage(url, -3);

            // Then
            verify(webPageService).fetchPage(url, 1, MAX_CONTENT_LENGTH, MAX_TOTAL_CHARS, TIMEOUT_SECONDS);
        }

        @Test
        @DisplayName("Given untitled page, When read, Then header falls back to part indicator")
        void readWebPage_WithoutTitle_ShouldUsePartIndicatorHeader() throws Exception {
            // Given
            String url = "https://example.com/page";
            when(webPageService.fetchPage(url, 1, MAX_CONTENT_LENGTH, MAX_TOTAL_CHARS, TIMEOUT_SECONDS))
                    .thenReturn(new PageSlice("", "Contenu sans titre.", 1, 1));

            // When
            ActionResult result = webPageActions.readWebPage(url, null);

            // Then
            List<String> data = (List<String>) result.getData();
            assertThat(data.get(0)).isEqualTo("Partie 1/1");
        }

        @Test
        @DisplayName("Given null URL, When reading, Then failure with explicit message, no crash")
        void readWebPage_WithNullUrl_ShouldReturnFailure() {
            // When
            ActionResult result = webPageActions.readWebPage(null, null);

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("URL invalide");
            verifyNoInteractions(webPageService);
        }

        @Test
        @DisplayName("Given ftp:// URL, When reading, Then failure with explicit message")
        void readWebPage_WithFtpUrl_ShouldReturnFailure() {
            // When
            ActionResult result = webPageActions.readWebPage("ftp://files.example.com/doc", null);

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("URL invalide");
        }

        @Test
        @DisplayName("Given SPA page, When reading, Then failure carries the JavaScript diagnostic")
        void readWebPage_WithSpaPage_ShouldReturnDiagnosticFailure() throws Exception {
            // Given
            String url = "https://spa.example.com/app";
            when(webPageService.fetchPage(url, 1, MAX_CONTENT_LENGTH, MAX_TOTAL_CHARS, TIMEOUT_SECONDS))
                    .thenThrow(new IOException(
                            "Page probablement dynamique (JavaScript requis) — contenu non extractible."));

            // When
            ActionResult result = webPageActions.readWebPage(url, null);

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("dynamique");
            assertThat(result.getMessage()).contains("JavaScript");
        }

        @Test
        @DisplayName("Given page timeout, When reading, Then timeout ActionResult, no crash")
        void readWebPage_WhenTimeout_ShouldReturnTimeoutResult() throws Exception {
            // Given
            String url = "https://slow-site.example.com";
            when(webPageService.fetchPage(url, 1, MAX_CONTENT_LENGTH, MAX_TOTAL_CHARS, TIMEOUT_SECONDS))
                    .thenThrow(new HttpTimeoutException("HTTP read timed out"));

            // When
            ActionResult result = webPageActions.readWebPage(url, null);

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("Délai d'attente dépassé");
            assertThat(result.getErrorType()).isEqualTo("TimeoutException");
        }

        @Test
        @DisplayName("Given HTTP 404 from service, When reading, Then failure with HTTP status in message")
        void readWebPage_WhenHttp404_ShouldReturnFailure() throws Exception {
            // Given
            String url = "https://example.com/missing";
            when(webPageService.fetchPage(url, 1, MAX_CONTENT_LENGTH, MAX_TOTAL_CHARS, TIMEOUT_SECONDS))
                    .thenThrow(new IOException("HTTP 404 pour " + url));

            // When
            ActionResult result = webPageActions.readWebPage(url, null);

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("HTTP 404");
        }

        @Test
        @DisplayName("Given malformed URL rejected by service, When reading, Then failure with 'URL invalide'")
        void readWebPage_WhenMalformedUrl_ShouldReturnFailure() throws Exception {
            // Given — passes the http:// prefix check but URI.create rejects it downstream
            String url = "https://exa mple.com/page";
            when(webPageService.fetchPage(url, 1, MAX_CONTENT_LENGTH, MAX_TOTAL_CHARS, TIMEOUT_SECONDS))
                    .thenThrow(new IllegalArgumentException("Illegal character in authority"));

            // When
            ActionResult result = webPageActions.readWebPage(url, null);

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("URL invalide");
        }

        @Test
        @DisplayName("Given circuit breaker open, When reading, Then failure with 'temporairement indisponible'")
        void readWebPage_WhenCircuitBreakerOpen_ShouldReturnFailure() throws Exception {
            // Given
            String url = "https://example.com/page";
            when(webPageService.fetchPage(url, 1, MAX_CONTENT_LENGTH, MAX_TOTAL_CHARS, TIMEOUT_SECONDS))
                    .thenThrow(CallNotPermittedException.createCallNotPermittedException(
                            CircuitBreaker.ofDefaults("webPage")));

            // When
            ActionResult result = webPageActions.readWebPage(url, null);

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("temporairement indisponible");
        }

        @Test
        @DisplayName("Given interrupted thread, When reading, Then failure and thread interrupt flag restored")
        void readWebPage_WhenInterrupted_ShouldReturnFailureAndRestoreFlag() throws Exception {
            // Given
            String url = "https://example.com/page";
            when(webPageService.fetchPage(url, 1, MAX_CONTENT_LENGTH, MAX_TOTAL_CHARS, TIMEOUT_SECONDS))
                    .thenThrow(new InterruptedException("Thread interrupted"));

            // When
            ActionResult result = webPageActions.readWebPage(url, null);

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("Lecture interrompue");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();

            // Clean up interrupt flag for test runner
            Thread.interrupted();
        }

        @Test
        @DisplayName("Given successful page read, Then LONGTASK start and end events are emitted")
        void readWebPage_Success_ShouldEmitStartAndEndFeedback() throws Exception {
            // Given
            when(webPageService.fetchPage(anyString(), anyInt(), anyInt(), anyInt(), anyInt()))
                    .thenReturn(new PageSlice("T", "contenu", 1, 1));

            // When
            webPageActions.readWebPage("https://example.com", null);

            // Then
            verify(centralFeedBackHandler, times(2)).handleFeedBack(any());
        }

        @Test
        @DisplayName("Given page read fails, Then LONGTASK end event is still emitted (finally block)")
        void readWebPage_Failure_ShouldStillEmitEndFeedback() throws Exception {
            // Given
            when(webPageService.fetchPage(anyString(), anyInt(), anyInt(), anyInt(), anyInt()))
                    .thenThrow(new IOException("Broken"));

            // When
            webPageActions.readWebPage("https://failing.com", null);

            // Then — both start and end should be called (finally block)
            verify(centralFeedBackHandler, times(2)).handleFeedBack(any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // WebPageService — pagination (PageFetcher mocké, ContentExtractor réel)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("WebPageService — pagination")
    class ServicePaginationTests {

        @Mock
        private PageFetcher pageFetcher;

        private WebPageService webPageService;

        @BeforeEach
        void setUp() {
            webPageService = new WebPageService(pageFetcher, new ContentExtractor());
        }

        private void givenPageWithText(String url, String title, String text) throws Exception {
            String html = "<html><head><title>" + title + "</title></head><body><article><p>"
                    + text + "</p></article></body></html>";
            when(pageFetcher.fetch(eq(url), anyInt()))
                    .thenReturn(new PageFetcher.FetchedPage(url, "text/html", html));
        }

        @Test
        @DisplayName("Given 9000-char content with 4000-char parts, Then part 1 of 3 with word-boundary cut")
        void fetchPage_WithLongText_ShouldSliceOnWordBoundaries() throws Exception {
            // Given — ~9000 chars of repeated words
            String text = "lorem ipsum dolor sit amet consectetur adipiscing elit ".repeat(160).trim();
            givenPageWithText("https://a.com/long", "Long", text);

            // When
            PageSlice part1 = webPageService.fetchPage("https://a.com/long", 1, 4000, 40000, 5);
            PageSlice part2 = webPageService.fetchPage("https://a.com/long", 2, 4000, 40000, 5);

            // Then
            assertThat(part1.totalPages()).isEqualTo(3);
            assertThat(part1.page()).isEqualTo(1);
            assertThat(part1.title()).isEqualTo("Long");
            assertThat(part1.content().length()).isLessThanOrEqualTo(4000);
            // Coupe sur frontière de mot : chaque tranche finit et commence sur un mot entier
            List<String> words = List.of("lorem", "ipsum", "dolor", "sit", "amet",
                    "consectetur", "adipiscing", "elit");
            String lastWordOfPart1 = part1.content().substring(part1.content().lastIndexOf(' ') + 1);
            String firstWordOfPart2 = part2.content().substring(0, part2.content().indexOf(' '));
            assertThat(words).contains(lastWordOfPart1);
            assertThat(words).contains(firstWordOfPart2);
            assertThat(part2.page()).isEqualTo(2);
        }

        @Test
        @DisplayName("Given out-of-range part number, Then clamped to last part")
        void fetchPage_WithPageBeyondTotal_ShouldClampToLast() throws Exception {
            // Given
            String text = "mot ".repeat(2500).trim(); // ~10000 chars → 3 parties
            givenPageWithText("https://a.com/long", "Long", text);

            // When
            PageSlice slice = webPageService.fetchPage("https://a.com/long", 99, 4000, 40000, 5);

            // Then
            assertThat(slice.page()).isEqualTo(slice.totalPages());
        }

        @Test
        @DisplayName("Given content over max-total-chars, Then processing is capped")
        void fetchPage_WithHugeText_ShouldCapTotalChars() throws Exception {
            // Given — 50k chars (13 parties sans cap), cap 8000 → au plus 3 parties
            // (la coupe sur frontière de mot peut laisser une petite tranche résiduelle)
            String text = "abcd ".repeat(10000).trim();
            givenPageWithText("https://a.com/huge", "Huge", text);

            // When
            PageSlice slice = webPageService.fetchPage("https://a.com/huge", 1, 4000, 8000, 5);

            // Then
            assertThat(slice.totalPages()).isBetween(2, 3);
        }

        @Test
        @DisplayName("Given a SPA page, Then IOException with JavaScript diagnostic")
        void fetchPage_WithSpaPage_ShouldThrowDiagnostic() throws Exception {
            // Given — gros HTML sans texte
            String html = "<html><head>" + "<script src='app.js'></script>".repeat(200)
                    + "</head><body><div id='root'></div></body></html>";
            when(pageFetcher.fetch(eq("https://spa.com/app"), anyInt()))
                    .thenReturn(new PageFetcher.FetchedPage("https://spa.com/app", "text/html", html));

            // When/Then
            assertThatThrownBy(() -> webPageService.fetchPage("https://spa.com/app", 1, 4000, 40000, 5))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("JavaScript requis");
        }
    }
}
