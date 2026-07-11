package org.arcos.UnitTests.Tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.arcos.Tools.SearchTool.BraveSearchService;
import org.arcos.Tools.SearchTool.BraveSearchService.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BraveSearchServiceTest {

    // ── Disponibilité (clé API injectée) ────────────────────────────────────

    @Test
    @DisplayName("Given blank API key, Then service is not available")
    void isAvailable_WithBlankKey_ShouldBeFalse() {
        BraveSearchService service = new BraveSearchService("");

        assertThat(service.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("Given an API key, Then service is available")
    void isAvailable_WithKey_ShouldBeTrue() {
        BraveSearchService service = new BraveSearchService("test-key");

        assertThat(service.isAvailable()).isTrue();
    }

    // ── Construction de l'URL (HttpClient mocké) ────────────────────────────

    @SuppressWarnings("unchecked")
    private static HttpRequest captureRequest(SearchOptions options) throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"web\":{\"results\":[]}}");
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        when(httpClient.send(captor.capture(), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        BraveSearchService service = new BraveSearchService(httpClient, new ObjectMapper(), "test-key");
        service.search("requête test", options);
        return captor.getValue();
    }

    @Test
    @DisplayName("Given default options (freshness ALL), Then URL contains no freshness parameter")
    void search_WithFreshnessAll_ShouldNotAppendFreshnessParam() throws Exception {
        HttpRequest request = captureRequest(SearchOptions.defaultOptions());

        assertThat(request.uri().toString()).doesNotContain("freshness");
    }

    @Test
    @DisplayName("Given freshness PAST_WEEK, Then URL contains freshness=pw")
    void search_WithFreshnessPastWeek_ShouldAppendFreshnessParam() throws Exception {
        HttpRequest request = captureRequest(SearchOptions.defaultOptions().withFreshness(Freshness.PAST_WEEK));

        assertThat(request.uri().toString()).contains("freshness=pw");
    }

    @Test
    @DisplayName("Given FR locale options, Then URL contains country, search_lang and extra_snippets")
    void search_WithFrenchLocale_ShouldAppendLocaleAndSnippetParams() throws Exception {
        HttpRequest request = captureRequest(
                SearchOptions.defaultOptions().withCountry("FR").withLanguage("fr"));

        String uri = request.uri().toString();
        assertThat(uri).contains("country=FR");
        assertThat(uri).contains("search_lang=fr");
        assertThat(uri).contains("extra_snippets=true");
        assertThat(uri).contains("text_decorations=false");
    }

    // ── Parsing de la réponse (HttpClient mocké) ────────────────────────────

    @SuppressWarnings("unchecked")
    private static SearchResult searchWithBody(String jsonBody) throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(jsonBody);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        BraveSearchService service = new BraveSearchService(httpClient, new ObjectMapper(), "test-key");
        return service.search("requête", SearchOptions.defaultOptions());
    }

    @Test
    @DisplayName("Given extra_snippets in response, Then items carry them")
    void search_WithExtraSnippets_ShouldParseThem() throws Exception {
        // Given
        String json = """
                {"web":{"results":[{"title":"T","url":"https://a.com","description":"D",
                  "extra_snippets":["premier extrait","second extrait"]}]}}
                """;

        // When
        SearchResult result = searchWithBody(json);

        // Then
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getExtraSnippets())
                .containsExactly("premier extrait", "second extrait");
    }

    @Test
    @DisplayName("Given no extra_snippets field, Then items carry an empty list")
    void search_WithoutExtraSnippets_ShouldReturnEmptyList() throws Exception {
        // Given
        String json = """
                {"web":{"results":[{"title":"T","url":"https://a.com","description":"D"}]}}
                """;

        // When
        SearchResult result = searchWithBody(json);

        // Then
        assertThat(result.getItems().get(0).getExtraSnippets()).isEmpty();
    }

    @Test
    @DisplayName("Given page_age but no published, Then date falls back to page_age date part")
    void search_WithPageAgeOnly_ShouldUsePageAgeAsDate() throws Exception {
        // Given
        String json = """
                {"web":{"results":[{"title":"T","url":"https://a.com","description":"D",
                  "page_age":"2026-07-01T08:30:00"}]}}
                """;

        // When
        SearchResult result = searchWithBody(json);

        // Then
        assertThat(result.getItems().get(0).getPublishedDate()).isPresent().hasValue("2026-07-01");
    }

    @Test
    @DisplayName("Given HTML-escaped title/description, Then entities are unescaped (TTS-safe)")
    void search_WithHtmlEntities_ShouldUnescape() throws Exception {
        // Given
        String json = """
                {"web":{"results":[{"title":"L&#x27;espace","url":"https://a.com",
                  "description":"Retour sur le vol d&#x27;essai &amp; ses suites."}]}}
                """;

        // When
        SearchResult result = searchWithBody(json);

        // Then
        assertThat(result.getItems().get(0).getTitle()).isEqualTo("L'espace");
        assertThat(result.getItems().get(0).getDescription())
                .isEqualTo("Retour sur le vol d'essai & ses suites.");
    }

    @Test
    @DisplayName("Given published present, Then published wins over page_age")
    void search_WithPublished_ShouldPreferPublished() throws Exception {
        // Given
        String json = """
                {"web":{"results":[{"title":"T","url":"https://a.com","description":"D",
                  "published":"2026-06-15","page_age":"2026-07-01T08:30:00"}]}}
                """;

        // When
        SearchResult result = searchWithBody(json);

        // Then
        assertThat(result.getItems().get(0).getPublishedDate()).isPresent().hasValue("2026-06-15");
    }

    // ── SearchOptions ───────────────────────────────────────────────────────

    @Test
    void searchOptions_DefaultValues_ShouldHaveSensibleDefaults() {
        // When
        SearchOptions options = SearchOptions.defaultOptions();

        // Then
        assertThat(options.getCount()).isEqualTo(10);
        assertThat(options.getOffset()).isEqualTo(0);
        assertThat(options.getSafeSearch()).isEqualTo(SafeSearch.MODERATE);
        assertThat(options.getFreshness()).isEqualTo(Freshness.ALL);
        assertThat(options.getCountry()).isNull();
        assertThat(options.getLanguage()).isNull();
    }

    @Test
    void searchOptions_CountClampedToMax20() {
        // When
        SearchOptions options = SearchOptions.defaultOptions().withCount(50);

        // Then
        assertThat(options.getCount()).isEqualTo(20);
    }

    @Test
    void searchOptions_CountClampedToMin1() {
        // When
        SearchOptions options = SearchOptions.defaultOptions().withCount(-5);

        // Then
        assertThat(options.getCount()).isEqualTo(1);
    }

    @Test
    void searchOptions_OffsetClampedToZero() {
        // When
        SearchOptions options = SearchOptions.defaultOptions().withOffset(-10);

        // Then
        assertThat(options.getOffset()).isEqualTo(0);
    }

    @Test
    void searchOptions_BuilderChaining_ShouldSetAllFields() {
        // When
        SearchOptions options = SearchOptions.defaultOptions()
                .withCount(5)
                .withOffset(10)
                .withSafeSearch(SafeSearch.STRICT)
                .withFreshness(Freshness.PAST_WEEK)
                .withCountry("FR")
                .withLanguage("fr");

        // Then
        assertThat(options.getCount()).isEqualTo(5);
        assertThat(options.getOffset()).isEqualTo(10);
        assertThat(options.getSafeSearch()).isEqualTo(SafeSearch.STRICT);
        assertThat(options.getFreshness()).isEqualTo(Freshness.PAST_WEEK);
        assertThat(options.getCountry()).isEqualTo("FR");
        assertThat(options.getLanguage()).isEqualTo("fr");
    }

    // ── SearchResult ────────────────────────────────────────────────────────

    @Test
    void searchResult_HasResults_WhenNotEmpty() {
        // Given
        SearchResultItem item = new SearchResultItem("Title", "https://example.com", "Desc", null);
        SearchResult result = new SearchResult("test query", List.of(item), 1);

        // Then
        assertThat(result.hasResults()).isTrue();
        assertThat(result.getQuery()).isEqualTo("test query");
        assertThat(result.getTotalResults()).isEqualTo(1);
        assertThat(result.getItems()).hasSize(1);
    }

    @Test
    void searchResult_HasNoResults_WhenEmpty() {
        // Given
        SearchResult result = new SearchResult("empty query", Collections.emptyList(), 0);

        // Then
        assertThat(result.hasResults()).isFalse();
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void searchResult_GetItems_ShouldReturnDefensiveCopy() {
        // Given
        SearchResultItem item = new SearchResultItem("Title", "https://example.com", "Desc", null);
        SearchResult result = new SearchResult("query", List.of(item), 1);

        // When
        List<SearchResultItem> items1 = result.getItems();
        List<SearchResultItem> items2 = result.getItems();

        // Then: different list instances (defensive copy)
        assertThat(items1).isNotSameAs(items2);
        assertThat(items1).isEqualTo(items2);
    }

    // ── SearchResultItem ────────────────────────────────────────────────────

    @Test
    void searchResultItem_GettersReturnCorrectValues() {
        // Given
        SearchResultItem item = new SearchResultItem("Title", "https://url.com", "Description", "2026-03-21");

        // Then
        assertThat(item.getTitle()).isEqualTo("Title");
        assertThat(item.getUrl()).isEqualTo("https://url.com");
        assertThat(item.getDescription()).isEqualTo("Description");
        assertThat(item.getPublishedDate()).isPresent().hasValue("2026-03-21");
    }

    @Test
    void searchResultItem_NullPublishedDate_ShouldReturnEmptyOptional() {
        // Given
        SearchResultItem item = new SearchResultItem("Title", "https://url.com", "Desc", null);

        // Then
        assertThat(item.getPublishedDate()).isEmpty();
    }

    // ── Enums ───────────────────────────────────────────────────────────────

    @Test
    void safeSearch_ShouldHaveCorrectApiValues() {
        assertThat(SafeSearch.OFF.getValue()).isEqualTo("off");
        assertThat(SafeSearch.MODERATE.getValue()).isEqualTo("moderate");
        assertThat(SafeSearch.STRICT.getValue()).isEqualTo("strict");
    }

    @Test
    void freshness_ShouldHaveCorrectApiValues() {
        assertThat(Freshness.ALL.getValue()).isEmpty();
        assertThat(Freshness.PAST_DAY.getValue()).isEqualTo("pd");
        assertThat(Freshness.PAST_WEEK.getValue()).isEqualTo("pw");
        assertThat(Freshness.PAST_MONTH.getValue()).isEqualTo("pm");
        assertThat(Freshness.PAST_YEAR.getValue()).isEqualTo("py");
    }
}
