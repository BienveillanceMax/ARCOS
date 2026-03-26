package org.arcos.IntegrationTests.Tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.arcos.IO.OuputHandling.StateHandler.CentralFeedBackHandler;
import org.arcos.Tools.Actions.ActionResult;
import org.arcos.Tools.Actions.GdeltActions;
import org.arcos.Tools.GdeltTool.GdeltAnalysisService;
import org.arcos.Tools.GdeltTool.GdeltDocClient;
import org.arcos.Tools.GdeltTool.GdeltDocClient.*;
import org.arcos.Tools.GdeltTool.GdeltProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests pour Rapport_Actualites (GdeltActions + GdeltAnalysisService + GdeltDocClient).
 *
 * Valide :
 * - AC5 : GDELT API repond => analyse retournee
 * - AC6 : GDELT indisponible => degradation gracieuse
 *
 * Teste a 3 niveaux :
 * - Action layer (GdeltActions)
 * - Service layer (GdeltAnalysisService — deja teste en GdeltAnalysisServiceTest, ajout de scenarios complementaires)
 * - HTTP layer (GdeltDocClient avec HttpClient mocke)
 */
@ExtendWith(MockitoExtension.class)
class GdeltToolIT {

    // ═══════════════════════════════════════════════════════════════════════════
    // GdeltActions — action layer
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GdeltActions — Rapport_Actualites")
    class GdeltActionsTests {

        @Mock
        private GdeltAnalysisService gdeltAnalysisService;

        @Mock
        private CentralFeedBackHandler centralFeedBackHandler;

        private GdeltActions gdeltActions;

        @BeforeEach
        void setUp() {
            gdeltActions = new GdeltActions(gdeltAnalysisService, centralFeedBackHandler);
        }

        // ── AC5 : normal operation — briefing mode ──────────────────────────

        @Test
        @DisplayName("Given null subject, When worldReport called, Then briefing mode is triggered")
        void worldReport_WithNullSubject_ShouldGenerateBriefing() {
            // Given
            String briefingReport = "=== BRIEFING ACTUALITES PERSONNALISE ===\n\nTheme 1: IA et emploi...";
            when(gdeltAnalysisService.generateBriefing()).thenReturn(briefingReport);

            // When
            ActionResult result = gdeltActions.worldReport(null);

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).isEqualTo("Rapport GDELT genere");
            List<String> data = (List<String>) result.getData();
            assertThat(data).hasSize(1);
            assertThat(data.getFirst()).contains("BRIEFING ACTUALITES PERSONNALISE");
            assertThat(result.getMetadata()).containsEntry("mode", "briefing");
            assertThat(result.getMetadata()).containsEntry("subject", null);
            verify(gdeltAnalysisService).generateBriefing();
            verify(gdeltAnalysisService, never()).analyzeSubject(any());
        }

        @Test
        @DisplayName("Given blank subject, When worldReport called, Then briefing mode is triggered")
        void worldReport_WithBlankSubject_ShouldGenerateBriefing() {
            // Given
            when(gdeltAnalysisService.generateBriefing()).thenReturn("briefing content");

            // When
            ActionResult result = gdeltActions.worldReport("   ");

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMetadata()).containsEntry("mode", "briefing");
            verify(gdeltAnalysisService).generateBriefing();
        }

        // ── AC5 : normal operation — analysis mode ──────────────────────────

        @Test
        @DisplayName("Given specific subject, When worldReport called, Then analysis mode is triggered")
        void worldReport_WithSubject_ShouldAnalyzeSubject() {
            // Given
            String analysisReport = "=== ANALYSE : ukraine ===\n\nArticles cles...";
            when(gdeltAnalysisService.analyzeSubject("ukraine")).thenReturn(analysisReport);

            // When
            ActionResult result = gdeltActions.worldReport("ukraine");

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).isEqualTo("Rapport GDELT genere");
            List<String> data = (List<String>) result.getData();
            assertThat(data.getFirst()).contains("ANALYSE : ukraine");
            assertThat(result.getMetadata()).containsEntry("mode", "analysis");
            assertThat(result.getMetadata()).containsEntry("subject", "ukraine");
            assertThat(result.getExecutionTimeMs()).isGreaterThanOrEqualTo(0);
            verify(gdeltAnalysisService).analyzeSubject("ukraine");
        }

        // ── AC6 : degradation gracieuse ─────────────────────────────────────

        @Test
        @DisplayName("Given GdeltAnalysisService throws, When worldReport called, Then failure with error message")
        void worldReport_WhenServiceThrows_ShouldReturnFailure() {
            // Given
            when(gdeltAnalysisService.analyzeSubject("failing"))
                    .thenThrow(new RuntimeException("GDELT API unreachable"));

            // When
            ActionResult result = gdeltActions.worldReport("failing");

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("Erreur GDELT");
            assertThat(result.getMessage()).contains("GDELT API unreachable");
            assertThat(result.getExecutionTimeMs()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("Given briefing generation throws, When worldReport called, Then failure with error message")
        void worldReport_WhenBriefingThrows_ShouldReturnFailure() {
            // Given
            when(gdeltAnalysisService.generateBriefing())
                    .thenThrow(new RuntimeException("No user profile loaded"));

            // When
            ActionResult result = gdeltActions.worldReport(null);

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("Erreur GDELT");
        }

        @Test
        @DisplayName("Circuit breaker fallback should return failure ActionResult")
        void worldReportFallback_ShouldReturnFailure() {
            // When
            ActionResult result = gdeltActions.worldReportFallback(
                    "ukraine", new RuntimeException("Too many failures"));

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("temporairement indisponible");
            assertThat(result.getExecutionTimeMs()).isEqualTo(0);
        }

        // ── Feedback events ─────────────────────────────────────────────────

        @Test
        @DisplayName("Given successful report, Then LONGTASK start and end events are emitted")
        void worldReport_Success_ShouldEmitFeedback() {
            // Given
            when(gdeltAnalysisService.analyzeSubject("test")).thenReturn("report");

            // When
            gdeltActions.worldReport("test");

            // Then
            verify(centralFeedBackHandler, times(2)).handleFeedBack(any());
        }

        @Test
        @DisplayName("Given report fails, Then LONGTASK end event is still emitted (finally block)")
        void worldReport_Failure_ShouldStillEmitEndFeedback() {
            // Given
            when(gdeltAnalysisService.analyzeSubject("fail"))
                    .thenThrow(new RuntimeException("error"));

            // When
            gdeltActions.worldReport("fail");

            // Then — both start and end feedback events
            verify(centralFeedBackHandler, times(2)).handleFeedBack(any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GdeltDocClient — HTTP layer
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GdeltDocClient — HTTP degradation")
    class GdeltDocClientDegradationTests {

        @Mock
        private HttpClient httpClient;

        @Mock
        private HttpResponse<String> httpResponse;

        private GdeltDocClient client;

        @BeforeEach
        void setUp() {
            GdeltProperties properties = new GdeltProperties();
            properties.setRateLimitMs(0);
            properties.setBaseUrl("https://api.gdeltproject.org/api/v2/doc/doc");
            properties.setTimeoutSeconds(10);
            client = new GdeltDocClient(httpClient, new ObjectMapper(), properties);
        }

        @Test
        @DisplayName("Given GDELT returns HTTP 429, When fetching articles, Then empty list (no crash)")
        void fetchArticles_WhenRateLimited_ShouldReturnEmptyList() throws Exception {
            // Given
            when(httpResponse.statusCode()).thenReturn(429);
            when(httpResponse.body()).thenReturn("Rate limit exceeded");
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenReturn(httpResponse);

            // When
            ArtlistResponse response = client.fetchArticles("test", "24hours", 5, "HybridRel");

            // Then
            assertThat(response.articles()).isEmpty();
        }

        @Test
        @DisplayName("Given GDELT returns HTTP 500, When fetching timeline, Then empty timeline (no crash)")
        void fetchTimeline_WhenServerError_ShouldReturnEmptyTimeline() throws Exception {
            // Given
            when(httpResponse.statusCode()).thenReturn(500);
            when(httpResponse.body()).thenReturn("Internal server error");
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenReturn(httpResponse);

            // When
            TimelineResponse response = client.fetchTimeline("ukraine", "timelinetone", "1week");

            // Then
            assertThat(response.timeline()).isEmpty();
        }

        @Test
        @DisplayName("Given network failure, When fetching articles, Then empty list (no crash)")
        void fetchArticles_WhenNetworkFailure_ShouldReturnEmptyList() throws Exception {
            // Given
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenThrow(new IOException("Connection refused"));

            // When
            ArtlistResponse response = client.fetchArticles("test", "24hours", 5, "HybridRel");

            // Then
            assertThat(response.articles()).isEmpty();
        }

        @Test
        @DisplayName("Given network timeout, When fetching timeline, Then empty timeline (no crash)")
        void fetchTimeline_WhenTimeout_ShouldReturnEmptyTimeline() throws Exception {
            // Given
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenThrow(new IOException("HTTP connect timed out"));

            // When
            TimelineResponse response = client.fetchTimeline("ukraine", "timelinetone", "1week");

            // Then
            assertThat(response.timeline()).isEmpty();
        }

        @Test
        @DisplayName("Given interrupted request, When fetching articles, Then empty list and interrupt consumed")
        void fetchArticles_WhenInterrupted_ShouldReturnEmptyList() throws Exception {
            // Given
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenThrow(new InterruptedException("Thread interrupted"));

            // When
            ArtlistResponse response = client.fetchArticles("test", "24hours", 5, "HybridRel");

            // Then
            assertThat(response.articles()).isEmpty();
            // GdeltDocClient sets interrupt flag
            assertThat(Thread.currentThread().isInterrupted()).isTrue();

            // Clean up
            Thread.interrupted();
        }

        @Test
        @DisplayName("Given malformed JSON response, When parsing artlist, Then empty list (no crash)")
        void fetchArticles_WithMalformedJson_ShouldReturnEmptyList() throws Exception {
            // Given
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn("{not valid json!!!}");
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenReturn(httpResponse);

            // When
            ArtlistResponse response = client.fetchArticles("test", "24hours", 5, "HybridRel");

            // Then
            assertThat(response.articles()).isEmpty();
        }

        @Test
        @DisplayName("Given valid artlist JSON, When parsing, Then articles are returned correctly")
        void fetchArticles_WithValidJson_ShouldParseCorrectly() throws Exception {
            // Given
            String json = """
                    {
                      "articles": [
                        {
                          "url": "https://lemonde.fr/ia",
                          "title": "L'IA transforme l'emploi",
                          "seendate": "20260326T100000Z",
                          "domain": "lemonde.fr",
                          "language": "French",
                          "sourcecountry": "France"
                        }
                      ]
                    }
                    """;
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(json);
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenReturn(httpResponse);

            // When
            ArtlistResponse response = client.fetchArticles("IA emploi", "24hours", 10, "HybridRel");

            // Then
            assertThat(response.articles()).hasSize(1);
            GdeltArticle article = response.articles().getFirst();
            assertThat(article.title()).isEqualTo("L'IA transforme l'emploi");
            assertThat(article.domain()).isEqualTo("lemonde.fr");
            assertThat(article.sourcecountry()).isEqualTo("France");
        }

        @Test
        @DisplayName("Given valid timeline JSON, When parsing, Then timeline data is returned correctly")
        void fetchTimeline_WithValidJson_ShouldParseCorrectly() throws Exception {
            // Given
            String json = """
                    {
                      "timeline": [
                        {
                          "series": "Tone",
                          "data": [
                            {"date": "20260324T000000Z", "value": -1.5},
                            {"date": "20260325T000000Z", "value": 0.8}
                          ]
                        }
                      ]
                    }
                    """;
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(json);
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenReturn(httpResponse);

            // When
            TimelineResponse response = client.fetchTimeline("ukraine", "timelinetone", "1week");

            // Then
            assertThat(response.timeline()).hasSize(1);
            assertThat(response.timeline().getFirst().series()).isEqualTo("Tone");
            assertThat(response.timeline().getFirst().data()).hasSize(2);
            assertThat(response.timeline().getFirst().data().get(0).value()).isEqualTo(-1.5);
            assertThat(response.timeline().getFirst().data().get(1).value()).isEqualTo(0.8);
        }
    }
}
