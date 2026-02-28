package org.arcos.UnitTests.Setup;

import org.arcos.Setup.ConfigurationModel;
import org.arcos.Setup.Persistence.ConfigurationWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void save_writesEnvFile_withMistralKey() throws IOException {
        // Given
        File envFile = tempDir.resolve(".env").toFile();
        File yamlFile = tempDir.resolve("application-local.yaml").toFile();
        ConfigurationWriter writer = new ConfigurationWriter(envFile, yamlFile);
        ConfigurationModel model = new ConfigurationModel();
        model.setMistralApiKey("sk-test-mistral-key");

        // When
        writer.save(model);

        // Then
        assertTrue(envFile.exists());
        String content = Files.readString(envFile.toPath());
        assertTrue(content.contains("MISTRALAI_API_KEY=sk-test-mistral-key"));
    }

    @Test
    void save_envFile_commentsMissingOptionalKeys() throws IOException {
        // Given
        File envFile = tempDir.resolve(".env").toFile();
        File yamlFile = tempDir.resolve("application-local.yaml").toFile();
        ConfigurationWriter writer = new ConfigurationWriter(envFile, yamlFile);
        ConfigurationModel model = new ConfigurationModel();
        model.setMistralApiKey("sk-test-key");
        // BRAVE et PORCUPINE non définis

        // When
        writer.save(model);

        // Then
        String content = Files.readString(envFile.toPath());
        assertTrue(content.contains("# BRAVE_SEARCH_API_KEY=your_key_here"));
        assertTrue(content.contains("# PORCUPINE_ACCESS_KEY=your_key_here"));
    }

    @Test
    void save_writesYamlFile_withAudioIndex() throws IOException {
        // Given
        File envFile = tempDir.resolve(".env").toFile();
        File yamlFile = tempDir.resolve("application-local.yaml").toFile();
        ConfigurationWriter writer = new ConfigurationWriter(envFile, yamlFile);
        ConfigurationModel model = new ConfigurationModel();
        model.setMistralApiKey("sk-test-key");
        model.setAudioDeviceIndex(5);

        // When
        writer.save(model);

        // Then
        assertTrue(yamlFile.exists());
        String content = Files.readString(yamlFile.toPath());
        assertTrue(content.contains("input-device-index: 5"));
    }

    @Test
    void save_writesYamlFile_withPersonalityProfile() throws IOException {
        // Given
        File envFile = tempDir.resolve(".env").toFile();
        File yamlFile = tempDir.resolve("application-local.yaml").toFile();
        ConfigurationWriter writer = new ConfigurationWriter(envFile, yamlFile);
        ConfigurationModel model = new ConfigurationModel();
        model.setMistralApiKey("sk-test-key");
        model.setPersonalityProfile("CALCIFER");

        // When
        writer.save(model);

        // Then
        String content = Files.readString(yamlFile.toPath());
        assertTrue(content.contains("profile: CALCIFER"));
    }

    @Test
    void save_noAudioDevice_skipsAudioSection() throws IOException {
        // Given
        File envFile = tempDir.resolve(".env").toFile();
        File yamlFile = tempDir.resolve("application-local.yaml").toFile();
        ConfigurationWriter writer = new ConfigurationWriter(envFile, yamlFile);
        ConfigurationModel model = new ConfigurationModel();
        model.setMistralApiKey("sk-test-key");
        // audioDeviceIndex reste -1

        // When
        writer.save(model);

        // Then
        String content = Files.readString(yamlFile.toPath());
        assertFalse(content.contains("input-device-index"));
    }

    @Test
    void save_allKeys_writesAllValues() throws IOException {
        // Given
        File envFile = tempDir.resolve(".env").toFile();
        File yamlFile = tempDir.resolve("application-local.yaml").toFile();
        ConfigurationWriter writer = new ConfigurationWriter(envFile, yamlFile);
        ConfigurationModel model = new ConfigurationModel();
        model.setMistralApiKey("sk-mistral");
        model.setBraveSearchApiKey("brave-key");
        model.setPorcupineAccessKey("porc-key");
        model.setAudioDeviceIndex(3);
        model.setPersonalityProfile("K2SO");

        // When
        writer.save(model);

        // Then
        String envContent = Files.readString(envFile.toPath());
        assertTrue(envContent.contains("MISTRALAI_API_KEY=sk-mistral"));
        assertTrue(envContent.contains("BRAVE_SEARCH_API_KEY=brave-key"));
        assertTrue(envContent.contains("PORCUPINE_ACCESS_KEY=porc-key"));

        String yamlContent = Files.readString(yamlFile.toPath());
        assertTrue(yamlContent.contains("input-device-index: 3"));
        assertTrue(yamlContent.contains("profile: K2SO"));
    }

    @Test
    void getters_returnConfiguredFiles() {
        // Given
        File envFile = new File("/tmp/.env");
        File yamlFile = new File("/tmp/app.yaml");
        ConfigurationWriter writer = new ConfigurationWriter(envFile, yamlFile);

        // Then
        assertEquals(envFile, writer.getEnvFile());
        assertEquals(yamlFile, writer.getLocalYamlFile());
    }
}
