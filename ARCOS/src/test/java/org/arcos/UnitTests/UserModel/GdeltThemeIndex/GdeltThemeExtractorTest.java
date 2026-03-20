package org.arcos.UnitTests.UserModel.GdeltThemeIndex;

import org.arcos.UserModel.DfsNavigator.UserContextFormatter;
import org.arcos.UserModel.GdeltThemeIndex.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GdeltThemeExtractorTest {

    @Mock
    private OllamaChatModel ollamaChatModel;

    @Mock
    private UserContextFormatter userContextFormatter;

    private GdeltThemeExtractor extractor;

    @BeforeEach
    void setUp() {
        GdeltThemeIndexProperties properties = new GdeltThemeIndexProperties();
        properties.setMaxKeywordsPerLeaf(5);
        properties.setExtractorTimeoutMs(5000);
        extractor = new GdeltThemeExtractor(ollamaChatModel, properties, userContextFormatter);
    }

    // ========== Parsing Tests ==========

    @Test
    void parsesValidFrenchEnglishAndGdeltThemeKeywords() {
        // Given
        String path = "4_Identity_Characteristics.Life_Beliefs.Political_Stance";
        when(userContextFormatter.humanReadablePath(path)).thenReturn("Croyances > Position politique");
        mockLlmResponse("gdelt:ENV_CLIMATECHANGE\nfr:transition écologique\nen:climate policy");

        // When
        List<GdeltKeyword> result = extractor.extract(path, "Écologiste convaincu");

        // Then
        assertThat(result).hasSize(3);
        assertThat(result.get(0)).isEqualTo(new GdeltKeyword("ENV_CLIMATECHANGE", KeywordLanguage.GDELT_THEME));
        assertThat(result.get(1)).isEqualTo(new GdeltKeyword("transition écologique", KeywordLanguage.FR));
        assertThat(result.get(2)).isEqualTo(new GdeltKeyword("climate policy", KeywordLanguage.EN));
    }

    @Test
    void skipsInvalidLinesWithWarning() {
        // Given
        String path = "5_Behavioral_Characteristics.Interests_and_Skills";
        when(userContextFormatter.humanReadablePath(path)).thenReturn("Intérêts");
        mockLlmResponse("fr:intelligence artificielle\nthis is garbage\nen:AI regulation\n---\nfr:tech");

        // When
        List<GdeltKeyword> result = extractor.extract(path, "Passionné par l'IA");

        // Then — only valid lines
        assertThat(result).hasSize(3);
        assertThat(result.get(0).term()).isEqualTo("intelligence artificielle");
        assertThat(result.get(1).term()).isEqualTo("AI regulation");
        assertThat(result.get(2).term()).isEqualTo("tech");
    }

    @Test
    void returnsEmptyListForEmptyLlmResponse() {
        // Given
        String path = "4_Identity_Characteristics.Life_Beliefs";
        when(userContextFormatter.humanReadablePath(path)).thenReturn("Croyances");
        mockLlmResponse("");

        // When
        List<GdeltKeyword> result = extractor.extract(path, "Some value");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyListForNullLlmResponse() {
        // Given
        String path = "4_Identity_Characteristics.Life_Beliefs";
        when(userContextFormatter.humanReadablePath(path)).thenReturn("Croyances");
        mockLlmResponse(null);

        // When
        List<GdeltKeyword> result = extractor.extract(path, "Some value");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyListOnLlmException() {
        // Given
        String path = "4_Identity_Characteristics.Life_Beliefs";
        when(userContextFormatter.humanReadablePath(path)).thenReturn("Croyances");
        when(ollamaChatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("Ollama down"));

        // When
        List<GdeltKeyword> result = extractor.extract(path, "Some value");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void truncatesKeywordsToMaxPerLeaf() {
        // Given
        String path = "5_Behavioral_Characteristics.Interests_and_Skills";
        when(userContextFormatter.humanReadablePath(path)).thenReturn("Intérêts");
        mockLlmResponse("fr:un\nen:two\nfr:trois\nen:four\nfr:cinq\nen:six\nfr:sept");

        // When
        List<GdeltKeyword> result = extractor.extract(path, "Many interests");

        // Then — max 5
        assertThat(result).hasSize(5);
    }

    @Test
    void skipsEmptyTerms() {
        // Given
        String path = "4_Identity_Characteristics.Life_Beliefs";
        when(userContextFormatter.humanReadablePath(path)).thenReturn("Croyances");
        mockLlmResponse("fr:\nen:valid keyword\nfr:   \nen:another");

        // When
        List<GdeltKeyword> result = extractor.extract(path, "Some value");

        // Then — empty terms skipped
        assertThat(result).hasSize(2);
        assertThat(result.get(0).term()).isEqualTo("valid keyword");
        assertThat(result.get(1).term()).isEqualTo("another");
    }

    @Test
    void handlesWindowsLineEndings() {
        // Given
        String path = "4_Identity_Characteristics.Life_Beliefs";
        when(userContextFormatter.humanReadablePath(path)).thenReturn("Croyances");
        mockLlmResponse("fr:écologie\r\nen:ecology\r\nfr:vert");

        // When
        List<GdeltKeyword> result = extractor.extract(path, "Ecolo");

        // Then
        assertThat(result).hasSize(3);
    }

    // ========== Helper ==========

    private void mockLlmResponse(String text) {
        if (text == null) {
            AssistantMessage message = new AssistantMessage(null);
            Generation generation = new Generation(message);
            ChatResponse response = new ChatResponse(List.of(generation));
            when(ollamaChatModel.call(any(Prompt.class))).thenReturn(response);
            return;
        }
        AssistantMessage message = new AssistantMessage(text);
        Generation generation = new Generation(message);
        ChatResponse response = new ChatResponse(List.of(generation));
        when(ollamaChatModel.call(any(Prompt.class))).thenReturn(response);
    }
}
