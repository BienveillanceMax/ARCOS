package org.arcos.UnitTests.Setup;

import org.arcos.Setup.ConfigurationModel;
import org.arcos.Setup.Persistence.EnvFileParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EnvFileParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parse_emptyFile_returnsEmptyMap() throws IOException {
        // Given
        File envFile = tempDir.resolve(".env").toFile();
        Files.writeString(envFile.toPath(), "");

        // When
        Map<String, String> result = EnvFileParser.parse(envFile);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void parse_nonExistentFile_returnsEmptyMap() throws IOException {
        // Given
        File envFile = new File("/tmp/nonexistent.env");

        // When
        Map<String, String> result = EnvFileParser.parse(envFile);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void parse_simpleKeyValue_parsesCorrectly() throws IOException {
        // Given
        File envFile = tempDir.resolve(".env").toFile();
        Files.writeString(envFile.toPath(), "MISTRALAI_API_KEY=sk-test-123\n");

        // When
        Map<String, String> result = EnvFileParser.parse(envFile);

        // Then
        assertEquals("sk-test-123", result.get("MISTRALAI_API_KEY"));
    }

    @Test
    void parse_commentLines_ignored() throws IOException {
        // Given
        File envFile = tempDir.resolve(".env").toFile();
        Files.writeString(envFile.toPath(),
                "# This is a comment\n" +
                "MISTRALAI_API_KEY=sk-test-123\n" +
                "# Another comment\n");

        // When
        Map<String, String> result = EnvFileParser.parse(envFile);

        // Then
        assertEquals(1, result.size());
        assertEquals("sk-test-123", result.get("MISTRALAI_API_KEY"));
    }

    @Test
    void parse_doubleQuotedValues_stripsQuotes() throws IOException {
        // Given
        File envFile = tempDir.resolve(".env").toFile();
        Files.writeString(envFile.toPath(), "MISTRALAI_API_KEY=\"sk-test-123\"\n");

        // When
        Map<String, String> result = EnvFileParser.parse(envFile);

        // Then
        assertEquals("sk-test-123", result.get("MISTRALAI_API_KEY"));
    }

    @Test
    void parse_singleQuotedValues_stripsQuotes() throws IOException {
        // Given
        File envFile = tempDir.resolve(".env").toFile();
        Files.writeString(envFile.toPath(), "MISTRALAI_API_KEY='sk-test-123'\n");

        // When
        Map<String, String> result = EnvFileParser.parse(envFile);

        // Then
        assertEquals("sk-test-123", result.get("MISTRALAI_API_KEY"));
    }

    @Test
    void parse_multipleKeys_parsesAll() throws IOException {
        // Given
        File envFile = tempDir.resolve(".env").toFile();
        Files.writeString(envFile.toPath(),
                "MISTRALAI_API_KEY=sk-test-123\n" +
                "BRAVE_SEARCH_API_KEY=brave-key-456\n" +
                "PORCUPINE_ACCESS_KEY=porc-key-789\n");

        // When
        Map<String, String> result = EnvFileParser.parse(envFile);

        // Then
        assertEquals(3, result.size());
        assertEquals("sk-test-123", result.get("MISTRALAI_API_KEY"));
        assertEquals("brave-key-456", result.get("BRAVE_SEARCH_API_KEY"));
        assertEquals("porc-key-789", result.get("PORCUPINE_ACCESS_KEY"));
    }

    @Test
    void loadIntoModel_setsAllKnownKeys() throws IOException {
        // Given
        File envFile = tempDir.resolve(".env").toFile();
        Files.writeString(envFile.toPath(),
                "MISTRALAI_API_KEY=sk-test-123\n" +
                "BRAVE_SEARCH_API_KEY=brave-456\n" +
                "PORCUPINE_ACCESS_KEY=porc-789\n");

        // When
        ConfigurationModel model = EnvFileParser.loadIntoModel(envFile);

        // Then
        assertEquals("sk-test-123", model.getMistralApiKey());
        assertEquals("brave-456", model.getBraveSearchApiKey());
        assertEquals("porc-789", model.getPorcupineAccessKey());
        assertTrue(model.isLoadedFromExistingEnv());
    }

    @Test
    void loadIntoModel_missingKeys_remainNull() throws IOException {
        // Given
        File envFile = tempDir.resolve(".env").toFile();
        Files.writeString(envFile.toPath(), "MISTRALAI_API_KEY=sk-test-123\n");

        // When
        ConfigurationModel model = EnvFileParser.loadIntoModel(envFile);

        // Then
        assertEquals("sk-test-123", model.getMistralApiKey());
        assertNull(model.getBraveSearchApiKey());
        assertNull(model.getPorcupineAccessKey());
    }
}
