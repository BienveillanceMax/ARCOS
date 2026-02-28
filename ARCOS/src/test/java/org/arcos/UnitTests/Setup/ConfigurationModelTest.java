package org.arcos.UnitTests.Setup;

import org.arcos.Setup.ConfigurationModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationModelTest {

    @Test
    void isMinimallyComplete_false_whenMistralKeyMissing() {
        // Given
        ConfigurationModel model = new ConfigurationModel();

        // Then
        assertFalse(model.isMinimallyComplete());
    }

    @Test
    void isMinimallyComplete_true_whenMistralKeyPresent() {
        // Given
        ConfigurationModel model = new ConfigurationModel();
        model.setMistralApiKey("sk-test-key-1234");

        // Then
        assertTrue(model.isMinimallyComplete());
    }

    @Test
    void isMinimallyComplete_false_whenMistralKeyBlank() {
        // Given
        ConfigurationModel model = new ConfigurationModel();
        model.setMistralApiKey("   ");

        // Then
        assertFalse(model.isMinimallyComplete());
    }

    @Test
    void defaultValues_areCorrect() {
        // Given
        ConfigurationModel model = new ConfigurationModel();

        // Then
        assertEquals(-1, model.getAudioDeviceIndex());
        assertEquals("DEFAULT", model.getPersonalityProfile());
        assertFalse(model.isLoadedFromExistingEnv());
    }

    @Test
    void keyValidation_storesAndRetrievesStatus() {
        // Given
        ConfigurationModel model = new ConfigurationModel();

        // When
        model.setKeyValidated("MISTRALAI_API_KEY", true);
        model.setKeyValidated("BRAVE_SEARCH_API_KEY", false);

        // Then
        assertTrue(model.isKeyValidated("MISTRALAI_API_KEY"));
        assertFalse(model.isKeyValidated("BRAVE_SEARCH_API_KEY"));
        assertNull(model.isKeyValidated("UNKNOWN_KEY"));
    }
}
