package org.arcos.UnitTests.UserModel.GdeltThemeIndex;

import org.arcos.UserModel.GdeltThemeIndex.*;
import org.arcos.UserModel.PersonaTree.PersonaTreeService;
import org.arcos.UserModel.PersonaTree.TreeOperationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GdeltThemeIndexServiceTest {

    @Mock
    private GdeltThemeIndexRepository repository;

    @Mock
    private GdeltThemeExtractor extractor;

    @Mock
    private PersonaTreeService personaTreeService;

    @TempDir
    Path tempDir;

    private GdeltThemeIndexService service;
    private GdeltThemeIndexProperties properties;

    @BeforeEach
    void setUp() {
        properties = new GdeltThemeIndexProperties();
        properties.setPath(tempDir.resolve("index.json").toString());
        service = new GdeltThemeIndexService(repository, extractor, properties, personaTreeService);
    }

    // ========== isGdeltRelevantPath ==========

    @Test
    void relevantPathReturnsTrueForPoliticalStance() {
        assertThat(GdeltThemeIndexService.isGdeltRelevantPath(
                "4_Identity_Characteristics.Life_Beliefs.Political_Stance")).isTrue();
    }

    @Test
    void relevantPathReturnsTrueForInterestsAndHobbies() {
        assertThat(GdeltThemeIndexService.isGdeltRelevantPath(
                "5_Behavioral_Characteristics.Interests_and_Skills.Interests_and_Hobbies")).isTrue();
    }

    @Test
    void relevantPathReturnsTrueForSocialIssues() {
        assertThat(GdeltThemeIndexService.isGdeltRelevantPath(
                "5_Behavioral_Characteristics.Social_Engagement.Social_Issues_of_Concern")).isTrue();
    }

    @Test
    void relevantPathReturnsTrueForIndustry() {
        assertThat(GdeltThemeIndexService.isGdeltRelevantPath(
                "4_Identity_Characteristics.Social_Identity.Occupational_Role.Industry")).isTrue();
    }

    @Test
    void relevantPathReturnsFalseForNonListedLeafUnderRelevantBranch() {
        // Soft_Skills is under Interests_and_Skills but NOT in the relevant leaf set
        assertThat(GdeltThemeIndexService.isGdeltRelevantPath(
                "5_Behavioral_Characteristics.Interests_and_Skills.Soft_Skills")).isFalse();
    }

    @Test
    void relevantPathReturnsFalseForPhysicalAppearance() {
        assertThat(GdeltThemeIndexService.isGdeltRelevantPath(
                "1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair")).isFalse();
    }

    @Test
    void relevantPathReturnsFalseForNull() {
        assertThat(GdeltThemeIndexService.isGdeltRelevantPath(null)).isFalse();
    }

    @Test
    void relevantPathReturnsFalseForPsychologicalState() {
        assertThat(GdeltThemeIndexService.isGdeltRelevantPath(
                "2_Psychological_Characteristics.Psychological_State.Mental_Health")).isFalse();
    }

    // ========== hashValue ==========

    @Test
    void hashValueProducesDeterministicOutput() {
        String hash1 = GdeltThemeIndexService.hashValue("test value");
        String hash2 = GdeltThemeIndexService.hashValue("test value");
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void hashValueDiffersForDifferentInputs() {
        String hash1 = GdeltThemeIndexService.hashValue("value A");
        String hash2 = GdeltThemeIndexService.hashValue("value B");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void hashValueReturnsEmptyForNull() {
        assertThat(GdeltThemeIndexService.hashValue(null)).isEmpty();
    }

    @Test
    void hashValueReturnsEmptyForEmpty() {
        assertThat(GdeltThemeIndexService.hashValue("")).isEmpty();
    }

    // ========== onLeafMutated ==========

    @Test
    void onLeafMutatedAddExtractsAndPersists() {
        // Given
        String path = "4_Identity_Characteristics.Life_Beliefs.Political_Stance";
        String value = "Milite pour l'écologie";
        List<GdeltKeyword> keywords = List.of(new GdeltKeyword("écologie", KeywordLanguage.FR));
        when(extractor.extract(path, value)).thenReturn(keywords);

        // When
        service.onLeafMutated(path, value, TreeOperationType.ADD);

        // Then
        assertThat(service.getIndex()).containsKey(path);
        assertThat(service.getIndex().get(path).keywords()).isEqualTo(keywords);
        verify(repository).save(any(), any());
    }

    @Test
    void onLeafMutatedUpdateReplacesEntry() {
        // Given — pre-populate index
        String path = "4_Identity_Characteristics.Life_Beliefs.Political_Stance";
        service.getIndex().put(path, new GdeltLeafThemes(path, "oldhash",
                List.of(new GdeltKeyword("old", KeywordLanguage.FR)), Instant.now()));

        String newValue = "S'intéresse à l'IA";
        List<GdeltKeyword> newKeywords = List.of(new GdeltKeyword("intelligence artificielle", KeywordLanguage.FR));
        when(extractor.extract(path, newValue)).thenReturn(newKeywords);

        // When
        service.onLeafMutated(path, newValue, TreeOperationType.UPDATE);

        // Then
        assertThat(service.getIndex().get(path).keywords()).isEqualTo(newKeywords);
        assertThat(service.getIndex().get(path).sourceHash()).isNotEqualTo("oldhash");
    }

    @Test
    void onLeafMutatedDeleteRemovesEntry() {
        // Given — pre-populate
        String path = "4_Identity_Characteristics.Life_Beliefs.Political_Stance";
        service.getIndex().put(path, new GdeltLeafThemes(path, "hash",
                List.of(new GdeltKeyword("kw", KeywordLanguage.FR)), Instant.now()));

        // When
        service.onLeafMutated(path, null, TreeOperationType.DELETE);

        // Then
        assertThat(service.getIndex()).doesNotContainKey(path);
        verify(repository).save(any(), any());
    }

    @Test
    void onLeafMutatedIgnoresIrrelevantPath() {
        // Given
        String path = "1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair";

        // When
        service.onLeafMutated(path, "brun", TreeOperationType.ADD);

        // Then
        assertThat(service.getIndex()).isEmpty();
        verify(extractor, never()).extract(any(), any());
        verify(repository, never()).save(any(), any());
    }

    @Test
    void onLeafMutatedDoesNotPersistOnExtractionFailure() {
        // Given
        String path = "4_Identity_Characteristics.Life_Beliefs.Political_Stance";
        when(extractor.extract(path, "value")).thenReturn(List.of()); // extraction failed

        // When
        service.onLeafMutated(path, "value", TreeOperationType.ADD);

        // Then
        assertThat(service.getIndex()).doesNotContainKey(path);
        verify(repository, never()).save(any(), any());
    }

    @Test
    void onLeafMutatedDeleteOnNonExistentEntryIsNoOp() {
        // Given — empty index
        String path = "4_Identity_Characteristics.Life_Beliefs.Political_Stance";

        // When
        service.onLeafMutated(path, null, TreeOperationType.DELETE);

        // Then
        verify(repository, never()).save(any(), any());
    }

    // ========== Reconciliation ==========

    @Test
    void reconcileExtractsMissingEntries() {
        // Given
        Map<String, String> leaves = new LinkedHashMap<>();
        leaves.put("4_Identity_Characteristics.Life_Beliefs.Political_Stance", "écologie");
        leaves.put("1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair", "brun"); // irrelevant
        when(personaTreeService.getNonEmptyLeaves()).thenReturn(leaves);
        when(repository.load(any())).thenReturn(new ConcurrentHashMap<>());
        when(extractor.extract(eq("4_Identity_Characteristics.Life_Beliefs.Political_Stance"), eq("écologie")))
                .thenReturn(List.of(new GdeltKeyword("écologie", KeywordLanguage.FR)));

        // When
        service.reconcile();

        // Then
        assertThat(service.getIndex()).hasSize(1);
        assertThat(service.getIndex()).containsKey("4_Identity_Characteristics.Life_Beliefs.Political_Stance");
    }

    @Test
    void reconcileReextractsStaleEntries() {
        // Given
        String path = "4_Identity_Characteristics.Life_Beliefs.Political_Stance";
        String oldValue = "ancienne valeur";
        String newValue = "nouvelle valeur";

        ConcurrentHashMap<String, GdeltLeafThemes> existingIndex = new ConcurrentHashMap<>();
        existingIndex.put(path, new GdeltLeafThemes(path,
                GdeltThemeIndexService.hashValue(oldValue),
                List.of(new GdeltKeyword("old", KeywordLanguage.FR)), Instant.now()));

        when(repository.load(any())).thenReturn(existingIndex);
        when(personaTreeService.getNonEmptyLeaves()).thenReturn(Map.of(path, newValue));
        when(extractor.extract(path, newValue))
                .thenReturn(List.of(new GdeltKeyword("new", KeywordLanguage.FR)));

        // When
        service.reconcile();

        // Then
        assertThat(service.getIndex().get(path).keywords().get(0).term()).isEqualTo("new");
    }

    @Test
    void reconcileRemovesOrphanedEntries() {
        // Given — index has an entry, but the tree leaf is now empty
        String path = "4_Identity_Characteristics.Life_Beliefs.Political_Stance";
        ConcurrentHashMap<String, GdeltLeafThemes> existingIndex = new ConcurrentHashMap<>();
        existingIndex.put(path, new GdeltLeafThemes(path, "hash",
                List.of(new GdeltKeyword("kw", KeywordLanguage.FR)), Instant.now()));

        when(repository.load(any())).thenReturn(existingIndex);
        when(personaTreeService.getNonEmptyLeaves()).thenReturn(Map.of()); // leaf gone

        // When
        service.reconcile();

        // Then
        assertThat(service.getIndex()).doesNotContainKey(path);
    }

    @Test
    void reconcileIsIdempotentWhenInSync() {
        // Given — index matches tree perfectly
        String path = "4_Identity_Characteristics.Life_Beliefs.Political_Stance";
        String value = "écologie";

        ConcurrentHashMap<String, GdeltLeafThemes> existingIndex = new ConcurrentHashMap<>();
        existingIndex.put(path, new GdeltLeafThemes(path,
                GdeltThemeIndexService.hashValue(value),
                List.of(new GdeltKeyword("écologie", KeywordLanguage.FR)), Instant.now()));

        when(repository.load(any())).thenReturn(existingIndex);
        when(personaTreeService.getNonEmptyLeaves()).thenReturn(Map.of(path, value));

        // When
        service.reconcile();

        // Then — no LLM calls, no persistence
        verify(extractor, never()).extract(any(), any());
        verify(repository, never()).save(any(), any());
    }

    @Test
    void reconcileDoesNotPersistFailedExtractions() {
        // Given
        String path = "5_Behavioral_Characteristics.Interests_and_Skills.Interests_and_Hobbies";
        when(repository.load(any())).thenReturn(new ConcurrentHashMap<>());
        when(personaTreeService.getNonEmptyLeaves()).thenReturn(Map.of(path, "programmation"));
        when(extractor.extract(path, "programmation")).thenReturn(List.of()); // failed

        // When
        service.reconcile();

        // Then — entry NOT in index, so next reconcile will retry
        assertThat(service.getIndex()).doesNotContainKey(path);
    }
}
