package org.arcos.UnitTests.UserModel.GdeltThemeIndex;

import org.arcos.UserModel.GdeltThemeIndex.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GdeltThemeIndexGateTest {

    @Mock
    private GdeltThemeIndexService service;

    private GdeltThemeIndexGate gate;

    @BeforeEach
    void setUp() {
        gate = new GdeltThemeIndexGate(service);
    }

    @Test
    void getAllKeywordsReturnsAggregatedDeduplicated() {
        // Given
        ConcurrentHashMap<String, GdeltLeafThemes> index = new ConcurrentHashMap<>();
        GdeltKeyword shared = new GdeltKeyword("climate", KeywordLanguage.EN);
        index.put("path1", new GdeltLeafThemes("path1", "h1",
                List.of(shared, new GdeltKeyword("écologie", KeywordLanguage.FR)),
                Instant.now()));
        index.put("path2", new GdeltLeafThemes("path2", "h2",
                List.of(shared, new GdeltKeyword("AI", KeywordLanguage.EN)),
                Instant.now()));
        when(service.getIndex()).thenReturn(index);

        // When
        List<GdeltKeyword> result = gate.getAllKeywords();

        // Then — "climate" appears once (deduplicated)
        assertThat(result).hasSize(3);
        assertThat(result).containsExactlyInAnyOrder(
                new GdeltKeyword("climate", KeywordLanguage.EN),
                new GdeltKeyword("écologie", KeywordLanguage.FR),
                new GdeltKeyword("AI", KeywordLanguage.EN));
    }

    @Test
    void getAllKeywordsReturnsEmptyForEmptyIndex() {
        // Given
        when(service.getIndex()).thenReturn(new ConcurrentHashMap<>());

        // When
        List<GdeltKeyword> result = gate.getAllKeywords();

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void getKeywordsForPathReturnsKeywords() {
        // Given
        ConcurrentHashMap<String, GdeltLeafThemes> index = new ConcurrentHashMap<>();
        List<GdeltKeyword> keywords = List.of(
                new GdeltKeyword("écologie", KeywordLanguage.FR),
                new GdeltKeyword("ecology", KeywordLanguage.EN));
        index.put("path1", new GdeltLeafThemes("path1", "h1", keywords, Instant.now()));
        when(service.getIndex()).thenReturn(index);

        // When
        List<GdeltKeyword> result = gate.getKeywordsForPath("path1");

        // Then
        assertThat(result).isEqualTo(keywords);
    }

    @Test
    void getKeywordsForPathReturnsEmptyForUnknownPath() {
        // Given
        when(service.getIndex()).thenReturn(new ConcurrentHashMap<>());

        // When
        List<GdeltKeyword> result = gate.getKeywordsForPath("unknown.path");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void getIndexedLeafCountReturnsCorrectCount() {
        // Given
        ConcurrentHashMap<String, GdeltLeafThemes> index = new ConcurrentHashMap<>();
        index.put("p1", new GdeltLeafThemes("p1", "h", List.of(), Instant.now()));
        index.put("p2", new GdeltLeafThemes("p2", "h", List.of(), Instant.now()));
        when(service.getIndex()).thenReturn(index);

        // When / Then
        assertThat(gate.getIndexedLeafCount()).isEqualTo(2);
    }

    @Test
    void isEmptyReturnsTrueForEmptyIndex() {
        // Given
        when(service.getIndex()).thenReturn(new ConcurrentHashMap<>());

        // When / Then
        assertThat(gate.isEmpty()).isTrue();
    }

    @Test
    void isEmptyReturnsFalseForNonEmptyIndex() {
        // Given
        ConcurrentHashMap<String, GdeltLeafThemes> index = new ConcurrentHashMap<>();
        index.put("p1", new GdeltLeafThemes("p1", "h", List.of(), Instant.now()));
        when(service.getIndex()).thenReturn(index);

        // When / Then
        assertThat(gate.isEmpty()).isFalse();
    }
}
