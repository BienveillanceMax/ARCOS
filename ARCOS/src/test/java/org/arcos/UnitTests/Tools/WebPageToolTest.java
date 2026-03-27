package org.arcos.UnitTests.Tools;

import org.arcos.IO.OuputHandling.StateHandler.CentralFeedBackHandler;
import org.arcos.Tools.Actions.ActionResult;
import org.arcos.Tools.Actions.WebPageActions;
import org.arcos.Tools.WebPageTool.WebPageService;
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
import static org.mockito.Mockito.*;

/**
 * Tests pour Lire_une_page_web (WebPageActions + WebPageService).
 *
 * Valide :
 * - AC3 : lecture de page web => contenu extrait (max 4000 chars)
 * - AC6 : service indisponible (timeout, IOException) => degradation gracieuse
 */
@ExtendWith(MockitoExtension.class)
class WebPageToolTest {

    @Mock
    private WebPageService webPageService;

    @Mock
    private CentralFeedBackHandler centralFeedBackHandler;

    private WebPageActions webPageActions;

    private static final int MAX_CONTENT_LENGTH = 4000;
    private static final int TIMEOUT_SECONDS = 15;

    @BeforeEach
    void setUp() {
        webPageActions = new WebPageActions(webPageService, centralFeedBackHandler,
                MAX_CONTENT_LENGTH, TIMEOUT_SECONDS);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AC3 : normal operation
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Lire_une_page_web — normal operation")
    class NormalOperationTests {

        @Test
        @DisplayName("Given valid URL, When page is fetched, Then content is returned in ActionResult")
        void readWebPage_WithValidUrl_ShouldReturnExtractedContent() throws Exception {
            // Given
            String url = "https://www.lemonde.fr/article-important";
            String content = "Voici le contenu principal de l'article sur l'intelligence artificielle.";
            when(webPageService.fetchAndExtract(url, MAX_CONTENT_LENGTH, TIMEOUT_SECONDS))
                    .thenReturn(content);

            // When
            ActionResult result = webPageActions.readWebPage(url);

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).isEqualTo("Page lue avec succès");
            List<String> data = (List<String>) result.getData();
            assertThat(data).hasSize(1);
            assertThat(data.getFirst()).isEqualTo(content);
            assertThat(result.getMetadata()).containsEntry("url", url);
            assertThat(result.getExecutionTimeMs()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("Given URL with long content, When page fetched, Then WebPageService handles truncation")
        void readWebPage_WithLongContent_ShouldReturnTruncatedContent() throws Exception {
            // Given
            String url = "https://www.example.com/long-article";
            // WebPageService handles truncation internally — we just verify the action passes it through
            String truncated = "A".repeat(4000) + " ... [contenu tronqué]";
            when(webPageService.fetchAndExtract(url, MAX_CONTENT_LENGTH, TIMEOUT_SECONDS))
                    .thenReturn(truncated);

            // When
            ActionResult result = webPageActions.readWebPage(url);

            // Then
            assertThat(result.isSuccess()).isTrue();
            List<String> data = (List<String>) result.getData();
            assertThat(data.getFirst()).contains("[contenu tronqué]");
            assertThat(data.getFirst().length()).isGreaterThan(MAX_CONTENT_LENGTH);
        }

        @Test
        @DisplayName("Given http:// URL (not https), When reading, Then page is fetched normally")
        void readWebPage_WithHttpUrl_ShouldSucceed() throws Exception {
            // Given
            String url = "http://plain.example.com/page";
            when(webPageService.fetchAndExtract(url, MAX_CONTENT_LENGTH, TIMEOUT_SECONDS))
                    .thenReturn("content");

            // When
            ActionResult result = webPageActions.readWebPage(url);

            // Then
            assertThat(result.isSuccess()).isTrue();
            verify(webPageService).fetchAndExtract(url, MAX_CONTENT_LENGTH, TIMEOUT_SECONDS);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AC3 + AC6 : input validation and degradation
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Lire_une_page_web — validation and degradation")
    class ValidationAndDegradationTests {

        @Test
        @DisplayName("Given null URL, When reading, Then failure with explicit message, no crash")
        void readWebPage_WithNullUrl_ShouldReturnFailure() {
            // When
            ActionResult result = webPageActions.readWebPage(null);

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("URL invalide");
            verifyNoInteractions(webPageService);
        }

        @Test
        @DisplayName("Given ftp:// URL, When reading, Then failure with explicit message")
        void readWebPage_WithFtpUrl_ShouldReturnFailure() {
            // When
            ActionResult result = webPageActions.readWebPage("ftp://files.example.com/doc");

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("URL invalide");
        }

        @Test
        @DisplayName("Given URL without protocol, When reading, Then failure with explicit message")
        void readWebPage_WithNoProtocol_ShouldReturnFailure() {
            // When
            ActionResult result = webPageActions.readWebPage("www.example.com");

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("URL invalide");
        }

        @Test
        @DisplayName("Given page timeout, When reading, Then timeout ActionResult, no crash")
        void readWebPage_WhenTimeout_ShouldReturnTimeoutResult() throws Exception {
            // Given
            String url = "https://slow-site.example.com";
            when(webPageService.fetchAndExtract(url, MAX_CONTENT_LENGTH, TIMEOUT_SECONDS))
                    .thenThrow(new HttpTimeoutException("HTTP read timed out"));

            // When
            ActionResult result = webPageActions.readWebPage(url);

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("Délai d'attente dépassé");
            assertThat(result.getErrorType()).isEqualTo("TimeoutException");
        }

        @Test
        @DisplayName("Given connection refused, When reading, Then failure with error details, no crash")
        void readWebPage_WhenConnectionRefused_ShouldReturnFailureWithDetails() throws Exception {
            // Given
            String url = "https://down-site.example.com";
            when(webPageService.fetchAndExtract(url, MAX_CONTENT_LENGTH, TIMEOUT_SECONDS))
                    .thenThrow(new IOException("Connection refused"));

            // When
            ActionResult result = webPageActions.readWebPage(url);

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("Erreur de lecture");
            assertThat(result.getMessage()).contains("Connection refused");
            assertThat(result.getExecutionTimeMs()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("Given interrupted thread, When reading, Then failure and thread interrupt flag restored")
        void readWebPage_WhenInterrupted_ShouldReturnFailureAndRestoreFlag() throws Exception {
            // Given
            String url = "https://example.com/page";
            when(webPageService.fetchAndExtract(url, MAX_CONTENT_LENGTH, TIMEOUT_SECONDS))
                    .thenThrow(new InterruptedException("Thread interrupted"));

            // When
            ActionResult result = webPageActions.readWebPage(url);

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("Lecture interrompue");
            // Thread interrupt flag should have been restored
            assertThat(Thread.currentThread().isInterrupted()).isTrue();

            // Clean up interrupt flag for test runner
            Thread.interrupted();
        }

        @Test
        @DisplayName("Circuit breaker fallback should return failure ActionResult")
        void readWebPageFallback_ShouldReturnFailure() {
            // When
            ActionResult result = webPageActions.readWebPageFallback(
                    "https://example.com", new RuntimeException("Too many failures"));

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("temporairement indisponible");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Feedback events
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Lire_une_page_web — UX feedback")
    class FeedbackTests {

        @Test
        @DisplayName("Given successful page read, Then LONGTASK start and end events are emitted")
        void readWebPage_Success_ShouldEmitStartAndEndFeedback() throws Exception {
            // Given
            when(webPageService.fetchAndExtract(anyString(), anyInt(), anyInt()))
                    .thenReturn("content");

            // When
            webPageActions.readWebPage("https://example.com");

            // Then
            verify(centralFeedBackHandler, times(2)).handleFeedBack(any());
        }

        @Test
        @DisplayName("Given page read fails, Then LONGTASK end event is still emitted (finally block)")
        void readWebPage_Failure_ShouldStillEmitEndFeedback() throws Exception {
            // Given
            when(webPageService.fetchAndExtract(anyString(), anyInt(), anyInt()))
                    .thenThrow(new IOException("Broken"));

            // When
            webPageActions.readWebPage("https://failing.com");

            // Then — both start and end should be called (finally block)
            verify(centralFeedBackHandler, times(2)).handleFeedBack(any());
        }
    }
}
