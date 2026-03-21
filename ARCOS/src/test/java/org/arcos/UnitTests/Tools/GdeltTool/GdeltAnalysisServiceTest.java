package org.arcos.UnitTests.Tools.GdeltTool;

import org.arcos.Tools.GdeltTool.GdeltAnalysisService;
import org.arcos.Tools.GdeltTool.GdeltDocClient;
import org.arcos.Tools.GdeltTool.GdeltDocClient.*;
import org.arcos.Tools.GdeltTool.GdeltProperties;
import org.arcos.UserModel.GdeltThemeIndex.GdeltKeyword;
import org.arcos.UserModel.GdeltThemeIndex.GdeltThemeIndexGate;
import org.arcos.UserModel.GdeltThemeIndex.KeywordLanguage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GdeltAnalysisServiceTest {

    @Mock
    private GdeltThemeIndexGate themeIndexGate;

    @Mock
    private GdeltDocClient docClient;

    private GdeltProperties properties;
    private GdeltAnalysisService service;

    @BeforeEach
    void setUp() {
        properties = new GdeltProperties();
        properties.setMaxArticles(10);
        properties.setMaxBriefingQueries(3);
        properties.setDefaultSort("HybridRel");
        properties.setDefaultTimespan("1week");
        service = new GdeltAnalysisService(themeIndexGate, docClient, properties);
    }

    // --- generateBriefing ---

    @Test
    void generateBriefing_withNoKeywords_shouldReturnInformativeMessage() {
        when(themeIndexGate.getAllKeywords()).thenReturn(List.of());

        String report = service.generateBriefing();

        assertThat(report).contains("Aucun centre d'interet");
        verifyNoInteractions(docClient);
    }

    @Test
    void generateBriefing_withKeywords_shouldReturnReportWithArticles() {
        List<GdeltKeyword> keywords = List.of(
                new GdeltKeyword("intelligence artificielle", KeywordLanguage.FR),
                new GdeltKeyword("TECH_AI", KeywordLanguage.GDELT_THEME)
        );
        when(themeIndexGate.getAllKeywords()).thenReturn(keywords);
        when(themeIndexGate.getIndexedLeafCount()).thenReturn(2);

        when(docClient.fetchArticles(anyString(), eq("24hours"), eq(10), eq("HybridRel")))
                .thenReturn(new ArtlistResponse(List.of(
                        new GdeltArticle("u1", "IA et emploi", "20260320T100000Z", "lemonde.fr", "French", "France"),
                        new GdeltArticle("u2", "AI regulation", "20260320T090000Z", "bbc.com", "English", "United Kingdom")
                )));
        when(docClient.fetchTimeline(anyString(), eq("timelinetone"), eq("1week")))
                .thenReturn(new TimelineResponse(List.of(
                        new TimelineSeries("Tone", List.of(
                                new TimelineDataPoint("20260318T000000Z", -1.5),
                                new TimelineDataPoint("20260320T000000Z", 0.3))))));

        String report = service.generateBriefing();

        assertThat(report).contains("BRIEFING ACTUALITES PERSONNALISE");
        assertThat(report).contains("IA et emploi");
        // Multi-country articles should be grouped
        assertThat(report).contains("[France]");
        assertThat(report).contains("[United Kingdom]");
    }

    @Test
    void generateBriefing_shouldQuoteMultiWordTermsAndPrefixThemes() {
        when(themeIndexGate.getAllKeywords()).thenReturn(List.of(
                new GdeltKeyword("changement climatique", KeywordLanguage.FR),
                new GdeltKeyword("MILITARY", KeywordLanguage.GDELT_THEME)
        ));
        when(themeIndexGate.getIndexedLeafCount()).thenReturn(1);
        when(docClient.fetchArticles(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(new ArtlistResponse(List.of()));
        when(docClient.fetchTimeline(anyString(), anyString(), anyString()))
                .thenReturn(new TimelineResponse(List.of()));

        service.generateBriefing();

        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(docClient, atLeastOnce()).fetchArticles(cap.capture(), anyString(), anyInt(), anyString());
        String q = String.join(" ", cap.getAllValues());
        assertThat(q).contains("\"changement climatique\"");
        assertThat(q).contains("theme:MILITARY");
    }

    // --- analyzeSubject: query construction ---

    @Test
    void analyzeSubject_shouldFilterToFrenchAndEnglishSources() {
        stubAllApiCalls();

        service.analyzeSubject("ukraine");

        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(docClient).fetchArticles(cap.capture(), eq("1week"), eq(10), eq("HybridRel"));
        assertThat(cap.getValue()).contains("sourcelang:french OR sourcelang:english");
    }

    @Test
    void analyzeSubject_shouldQuoteMultiWordSubjects() {
        stubAllApiCalls();

        service.analyzeSubject("intelligence artificielle");

        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(docClient).fetchArticles(cap.capture(), anyString(), anyInt(), anyString());
        assertThat(cap.getValue()).startsWith("\"intelligence artificielle\"");
    }

    @Test
    void analyzeSubject_shouldCallVolrawNotVol() {
        stubAllApiCalls();

        service.analyzeSubject("test");

        verify(docClient).fetchTimeline(anyString(), eq("timelinevolraw"), anyString());
        verify(docClient, never()).fetchTimeline(anyString(), eq("timelinevol"), anyString());
    }

    // --- analyzeSubject: full report ---

    @Test
    void analyzeSubject_fullReport_shouldContainAllSections() {
        // Given — articles from 2 countries
        when(docClient.fetchArticles(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(new ArtlistResponse(List.of(
                        new GdeltArticle("u1", "Conflit en Ukraine", "20260320T120000Z", "lemonde.fr", "French", "France"),
                        new GdeltArticle("u2", "Ukraine update", "20260320T110000Z", "bbc.com", "English", "United Kingdom")
                )));
        // Volume raw — absolute counts with a rise
        when(docClient.fetchTimeline(anyString(), eq("timelinevolraw"), anyString()))
                .thenReturn(new TimelineResponse(List.of(
                        new TimelineSeries("Article Count", List.of(
                                new TimelineDataPoint("20260316T000000Z", 100),
                                new TimelineDataPoint("20260317T000000Z", 120),
                                new TimelineDataPoint("20260319T000000Z", 200),
                                new TimelineDataPoint("20260320T000000Z", 250))))));
        // Global tone
        when(docClient.fetchTimeline(anyString(), eq("timelinetone"), eq("3months")))
                .thenReturn(new TimelineResponse(List.of(
                        new TimelineSeries("Tone", List.of(
                                new TimelineDataPoint("20260101T000000Z", -3.2),
                                new TimelineDataPoint("20260201T000000Z", -4.1),
                                new TimelineDataPoint("20260301T000000Z", -2.8),
                                new TimelineDataPoint("20260320T000000Z", -1.5))))));
        // Geo — 3 countries
        when(docClient.fetchTimeline(anyString(), eq("timelinesourcecountry"), anyString()))
                .thenReturn(new TimelineResponse(List.of(
                        new TimelineSeries("United States Volume Intensity", List.of(new TimelineDataPoint("d", 15.2))),
                        new TimelineSeries("France Volume Intensity", List.of(new TimelineDataPoint("d", 8.5))),
                        new TimelineSeries("United Kingdom Volume Intensity", List.of(new TimelineDataPoint("d", 6.3))))));
        // Country-specific tones for narrative divergence
        when(docClient.fetchTimeline(contains("sourcecountry:united states"), eq("timelinetone"), anyString()))
                .thenReturn(new TimelineResponse(List.of(
                        new TimelineSeries("Tone", List.of(new TimelineDataPoint("d", -4.5), new TimelineDataPoint("d", -3.8))))));
        when(docClient.fetchTimeline(contains("sourcecountry:france"), eq("timelinetone"), anyString()))
                .thenReturn(new TimelineResponse(List.of(
                        new TimelineSeries("Tone", List.of(new TimelineDataPoint("d", -1.2), new TimelineDataPoint("d", -0.8))))));

        // When
        String report = service.analyzeSubject("ukraine");

        // Then — all sections present
        assertThat(report).contains("ANALYSE : ukraine");
        // Articles grouped by country
        assertThat(report).contains("[France]");
        assertThat(report).contains("[United Kingdom]");
        assertThat(report).contains("Conflit en Ukraine");
        // Volume with absolute count
        assertThat(report).contains("Attention mediatique");
        assertThat(report).contains("articles recenses");
        // Tone
        assertThat(report).contains("Sentiment des medias");
        assertThat(report).contains("devenue plus positive");
        // Geo
        assertThat(report).contains("United States (51%)");
        // Narrative divergence — US is more negative than France
        assertThat(report).contains("Comparaison des perspectives nationales");
        assertThat(report).contains("United States");
        assertThat(report).contains("France");
    }

    // --- analyzeSubject: narrative divergence ---

    @Test
    void analyzeSubject_narrativeDivergence_shouldDetectStrongDivergence() {
        when(docClient.fetchArticles(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(new ArtlistResponse(List.of()));
        when(docClient.fetchTimeline(anyString(), eq("timelinevolraw"), anyString()))
                .thenReturn(new TimelineResponse(List.of()));
        when(docClient.fetchTimeline(anyString(), eq("timelinetone"), eq("3months")))
                .thenReturn(new TimelineResponse(List.of()));
        // Geo: 2 countries
        when(docClient.fetchTimeline(anyString(), eq("timelinesourcecountry"), anyString()))
                .thenReturn(new TimelineResponse(List.of(
                        new TimelineSeries("Russia Volume Intensity", List.of(new TimelineDataPoint("d", 30.0))),
                        new TimelineSeries("United States Volume Intensity", List.of(new TimelineDataPoint("d", 25.0))))));
        // Russia very positive, US very negative — strong divergence
        when(docClient.fetchTimeline(contains("sourcecountry:russia"), eq("timelinetone"), anyString()))
                .thenReturn(new TimelineResponse(List.of(
                        new TimelineSeries("Tone", List.of(new TimelineDataPoint("d", 3.0), new TimelineDataPoint("d", 2.5))))));
        when(docClient.fetchTimeline(contains("sourcecountry:united states"), eq("timelinetone"), anyString()))
                .thenReturn(new TimelineResponse(List.of(
                        new TimelineSeries("Tone", List.of(new TimelineDataPoint("d", -4.0), new TimelineDataPoint("d", -3.5))))));

        String report = service.analyzeSubject("sanctions");

        assertThat(report).contains("DIVERGENCE NARRATIVE FORTE");
        assertThat(report).contains("Russia");
        assertThat(report).contains("United States");
    }

    @Test
    void analyzeSubject_narrativeDivergence_noGeoData_shouldSkipSection() {
        stubAllApiCalls();

        String report = service.analyzeSubject("obscure topic");

        assertThat(report).doesNotContain("Comparaison des perspectives nationales");
    }

    // --- analyzeSubject: degraded mode ---

    @Test
    void analyzeSubject_withEmptyApiResponses_shouldReturnDegradedReport() {
        stubAllApiCalls();

        String report = service.analyzeSubject("sujet inexistant");

        assertThat(report).contains("ANALYSE : sujet inexistant");
        assertThat(report).contains("Aucun article trouve");
        assertThat(report).contains("indisponibles");
    }

    // --- Volume analysis ---

    @Test
    void analyzeSubject_volumeSpike_shouldBeDetectedWithAbsoluteCount() {
        when(docClient.fetchArticles(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(new ArtlistResponse(List.of()));
        when(docClient.fetchTimeline(anyString(), eq("timelinevolraw"), anyString()))
                .thenReturn(new TimelineResponse(List.of(
                        new TimelineSeries("Article Count", List.of(
                                new TimelineDataPoint("20260316T000000Z", 10),
                                new TimelineDataPoint("20260317T000000Z", 12),
                                new TimelineDataPoint("20260318T000000Z", 8),
                                new TimelineDataPoint("20260319T000000Z", 200))))));
        when(docClient.fetchTimeline(anyString(), eq("timelinetone"), anyString()))
                .thenReturn(new TimelineResponse(List.of()));
        when(docClient.fetchTimeline(anyString(), eq("timelinesourcecountry"), anyString()))
                .thenReturn(new TimelineResponse(List.of()));

        String report = service.analyzeSubject("test");

        assertThat(report).contains("Pic d'attention");
        assertThat(report).contains("200 articles"); // absolute count in spike
        assertThat(report).contains("230 articles recenses"); // total count
    }

    // --- Geo coverage ---

    @Test
    void analyzeSubject_geoDominatedByOneCountry_shouldDetectConcentration() {
        when(docClient.fetchArticles(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(new ArtlistResponse(List.of()));
        when(docClient.fetchTimeline(anyString(), eq("timelinevolraw"), anyString()))
                .thenReturn(new TimelineResponse(List.of()));
        when(docClient.fetchTimeline(anyString(), eq("timelinetone"), eq("3months")))
                .thenReturn(new TimelineResponse(List.of()));
        when(docClient.fetchTimeline(anyString(), eq("timelinesourcecountry"), anyString()))
                .thenReturn(new TimelineResponse(List.of(
                        new TimelineSeries("Norway Volume Intensity", List.of(new TimelineDataPoint("d", 60.0))),
                        new TimelineSeries("Sweden Volume Intensity", List.of(new TimelineDataPoint("d", 5.0))))));
        // Country tone calls triggered by 2 countries in geo
        when(docClient.fetchTimeline(contains("sourcecountry:"), eq("timelinetone"), anyString()))
                .thenReturn(new TimelineResponse(List.of()));

        String report = service.analyzeSubject("test");

        assertThat(report).contains("Norway (92%)");
        assertThat(report).contains("dominee par Norway");
    }

    // --- Helpers ---

    @Test
    void formatDate_shouldConvertGdeltDate() {
        when(docClient.fetchArticles(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(new ArtlistResponse(List.of(
                        new GdeltArticle("u", "Title", "20260320T143000Z", "d.com", "French", "France"))));
        stubTimelineCalls();

        String report = service.analyzeSubject("test");
        assertThat(report).contains("20/03/2026 14:30");
    }

    // --- Test helpers ---

    private void stubAllApiCalls() {
        when(docClient.fetchArticles(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(new ArtlistResponse(List.of()));
        stubTimelineCalls();
    }

    private void stubTimelineCalls() {
        when(docClient.fetchTimeline(anyString(), anyString(), anyString()))
                .thenReturn(new TimelineResponse(List.of()));
    }
}
