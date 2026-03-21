package org.arcos.UnitTests.Tools;

import org.arcos.Tools.SearchTool.BraveSearchService;
import org.arcos.Tools.SearchTool.BraveSearchService.*;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BraveSearchServiceTest {

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
        assertThat(item.getExtractedContent()).isEmpty();
    }

    @Test
    void searchResultItem_NullPublishedDate_ShouldReturnEmptyOptional() {
        // Given
        SearchResultItem item = new SearchResultItem("Title", "https://url.com", "Desc", null);

        // Then
        assertThat(item.getPublishedDate()).isEmpty();
    }

    @Test
    void searchResultItem_SetExtractedContent_ShouldBeRetrievable() {
        // Given
        SearchResultItem item = new SearchResultItem("Title", "https://url.com", "Desc", null);

        // When
        item.setExtractedContent("Extracted text content");

        // Then
        assertThat(item.getExtractedContent()).isPresent().hasValue("Extracted text content");
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
