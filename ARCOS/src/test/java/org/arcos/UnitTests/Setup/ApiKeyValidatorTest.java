package org.arcos.UnitTests.Setup;

import org.arcos.Setup.Validation.ApiKeyValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyValidatorTest {

    @Test
    void validateMistralKey_null_returnsInvalid() {
        // Given
        ApiKeyValidator validator = new ApiKeyValidator();

        // When
        ApiKeyValidator.ValidationResult result = validator.validateMistralKey(null);

        // Then
        assertFalse(result.valid());
        assertTrue(result.message().contains("vide"));
    }

    @Test
    void validateMistralKey_blank_returnsInvalid() {
        // Given
        ApiKeyValidator validator = new ApiKeyValidator();

        // When
        ApiKeyValidator.ValidationResult result = validator.validateMistralKey("  ");

        // Then
        assertFalse(result.valid());
    }

    @Test
    void validateBraveKey_null_returnsInvalid() {
        // Given
        ApiKeyValidator validator = new ApiKeyValidator();

        // When
        ApiKeyValidator.ValidationResult result = validator.validateBraveKey(null);

        // Then
        assertFalse(result.valid());
    }

    @Test
    void validatePorcupineKey_null_returnsInvalid() {
        // Given
        ApiKeyValidator validator = new ApiKeyValidator();

        // When
        ApiKeyValidator.ValidationResult result = validator.validatePorcupineKey(null);

        // Then
        assertFalse(result.valid());
    }

    @Test
    void validatePorcupineKey_shortKey_returnsInvalid() {
        // Given
        ApiKeyValidator validator = new ApiKeyValidator();

        // When
        ApiKeyValidator.ValidationResult result = validator.validatePorcupineKey("short");

        // Then
        assertFalse(result.valid());
        assertTrue(result.message().contains("courte"));
    }

    @Test
    void validatePorcupineKey_longEnoughKey_returnsValid() {
        // Given
        ApiKeyValidator validator = new ApiKeyValidator();
        String longKey = "A".repeat(30); // 30 chars > min 20

        // When
        ApiKeyValidator.ValidationResult result = validator.validatePorcupineKey(longKey);

        // Then
        assertTrue(result.valid());
    }

    @Test
    void maskKey_null_returnsMasked() {
        assertEquals("****", ApiKeyValidator.maskKey(null));
    }

    @Test
    void maskKey_shortKey_returnsMasked() {
        assertEquals("****", ApiKeyValidator.maskKey("ab"));
    }

    @Test
    void maskKey_normalKey_showsLastFourChars() {
        // Given
        String key = "sk-test-key-ABCD";

        // When
        String masked = ApiKeyValidator.maskKey(key);

        // Then
        assertTrue(masked.endsWith("ABCD"));
        assertTrue(masked.startsWith("****"));
    }

    @Test
    void validationResult_ok_isValid() {
        ApiKeyValidator.ValidationResult result = ApiKeyValidator.ValidationResult.ok();
        assertTrue(result.valid());
        assertNotNull(result.message());
    }

    @Test
    void validationResult_invalid_isNotValid() {
        ApiKeyValidator.ValidationResult result = ApiKeyValidator.ValidationResult.invalid("test error");
        assertFalse(result.valid());
        assertEquals("test error", result.message());
    }
}
