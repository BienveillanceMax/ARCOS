package org.arcos.UnitTests.UserModel.GdeltThemeIndex;

import org.arcos.UserModel.GdeltThemeIndex.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class GdeltThemeIndexRepositoryTest {

    private GdeltThemeIndexRepository repository;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        repository = new GdeltThemeIndexRepository();
    }

    // ========== Load Tests ==========

    @Test
    void loadReturnsEmptyMapForMissingFile() {
        // Given
        Path nonExistent = tempDir.resolve("does-not-exist.json");

        // When
        ConcurrentHashMap<String, GdeltLeafThemes> result = repository.load(nonExistent);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void loadReturnsEmptyMapForCorruptedFile() throws IOException {
        // Given
        Path corrupted = tempDir.resolve("corrupted.json");
        Files.writeString(corrupted, "{ this is not valid JSON !@#$%");

        // When
        ConcurrentHashMap<String, GdeltLeafThemes> result = repository.load(corrupted);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void loadReturnsEmptyMapWhenEntriesKeyMissing() throws IOException {
        // Given
        Path file = tempDir.resolve("no-entries.json");
        Files.writeString(file, "{\"version\": 1}");

        // When
        ConcurrentHashMap<String, GdeltLeafThemes> result = repository.load(file);

        // Then
        assertThat(result).isEmpty();
    }

    // ========== Save + Load Round-trip Tests ==========

    @Test
    void saveAndLoadRoundtrip() {
        // Given
        Path file = tempDir.resolve("index.json");
        ConcurrentHashMap<String, GdeltLeafThemes> entries = new ConcurrentHashMap<>();
        entries.put("4_Identity_Characteristics.Life_Beliefs.Public_Interest_Engagement",
                new GdeltLeafThemes(
                        "4_Identity_Characteristics.Life_Beliefs.Public_Interest_Engagement",
                        "abc123",
                        List.of(
                                new GdeltKeyword("transition écologique", KeywordLanguage.FR),
                                new GdeltKeyword("climate policy", KeywordLanguage.EN)
                        ),
                        Instant.parse("2026-03-20T14:30:00Z")
                ));

        // When
        repository.save(entries, file);
        ConcurrentHashMap<String, GdeltLeafThemes> loaded = repository.load(file);

        // Then
        assertThat(loaded).hasSize(1);
        GdeltLeafThemes entry = loaded.get("4_Identity_Characteristics.Life_Beliefs.Public_Interest_Engagement");
        assertThat(entry).isNotNull();
        assertThat(entry.sourceHash()).isEqualTo("abc123");
        assertThat(entry.keywords()).hasSize(2);
        assertThat(entry.keywords().get(0).term()).isEqualTo("transition écologique");
        assertThat(entry.keywords().get(0).language()).isEqualTo(KeywordLanguage.FR);
        assertThat(entry.keywords().get(1).term()).isEqualTo("climate policy");
        assertThat(entry.keywords().get(1).language()).isEqualTo(KeywordLanguage.EN);
        assertThat(entry.indexedAt()).isEqualTo(Instant.parse("2026-03-20T14:30:00Z"));
    }

    @Test
    void saveAndLoadPreservesMultipleEntries() {
        // Given
        Path file = tempDir.resolve("multi.json");
        ConcurrentHashMap<String, GdeltLeafThemes> entries = new ConcurrentHashMap<>();
        entries.put("path.one", new GdeltLeafThemes("path.one", "h1",
                List.of(new GdeltKeyword("keyword1", KeywordLanguage.FR)), Instant.now()));
        entries.put("path.two", new GdeltLeafThemes("path.two", "h2",
                List.of(new GdeltKeyword("keyword2", KeywordLanguage.EN)), Instant.now()));
        entries.put("path.three", new GdeltLeafThemes("path.three", "h3",
                List.of(new GdeltKeyword("keyword3", KeywordLanguage.FR)), Instant.now()));

        // When
        repository.save(entries, file);
        ConcurrentHashMap<String, GdeltLeafThemes> loaded = repository.load(file);

        // Then
        assertThat(loaded).hasSize(3);
        assertThat(loaded).containsKeys("path.one", "path.two", "path.three");
    }

    // ========== File Format Tests ==========

    @Test
    void savedFileContainsVersionField() throws IOException {
        // Given
        Path file = tempDir.resolve("versioned.json");
        repository.save(new ConcurrentHashMap<>(), file);

        // When
        String content = Files.readString(file);

        // Then
        assertThat(content).contains("\"version\" : 1");
    }

    @Test
    void savedFileContainsIso8601Dates() throws IOException {
        // Given
        Path file = tempDir.resolve("dates.json");
        ConcurrentHashMap<String, GdeltLeafThemes> entries = new ConcurrentHashMap<>();
        entries.put("path", new GdeltLeafThemes("path", "hash",
                List.of(new GdeltKeyword("kw", KeywordLanguage.FR)),
                Instant.parse("2026-03-20T14:30:00Z")));

        // When
        repository.save(entries, file);
        String content = Files.readString(file);

        // Then
        assertThat(content).contains("2026-03-20T14:30:00Z");
        assertThat(content).doesNotContain("1742480"); // no epoch millis
    }

    @Test
    void saveCreatesParentDirectories() {
        // Given
        Path nested = tempDir.resolve("a/b/c/index.json");

        // When
        repository.save(new ConcurrentHashMap<>(), nested);

        // Then
        assertThat(Files.exists(nested)).isTrue();
    }

    @Test
    void atomicWriteDoesNotLeaveTmpFile() {
        // Given
        Path file = tempDir.resolve("atomic.json");

        // When
        repository.save(new ConcurrentHashMap<>(), file);

        // Then
        assertThat(Files.exists(file)).isTrue();
        assertThat(Files.exists(Path.of(file + ".tmp"))).isFalse();
    }

    @Test
    void saveAndLoadEmptyIndex() {
        // Given
        Path file = tempDir.resolve("empty.json");
        ConcurrentHashMap<String, GdeltLeafThemes> empty = new ConcurrentHashMap<>();

        // When
        repository.save(empty, file);
        ConcurrentHashMap<String, GdeltLeafThemes> loaded = repository.load(file);

        // Then
        assertThat(loaded).isEmpty();
    }
}
