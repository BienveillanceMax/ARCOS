package org.arcos.UnitTests.UserModel.DfsNavigator;

import org.arcos.UserModel.DfsNavigator.CrossEncoderService;
import org.arcos.UserModel.UserModelProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CrossEncoderServiceTest {

    @Test
    void shouldGracefullyDegradeWhenModelNotFound() {
        // Given — model path points to non-existent file
        UserModelProperties properties = new UserModelProperties();
        properties.setCrossEncoderModelPath("/non/existent/model.onnx");
        properties.setCrossEncoderTokenizerPath("/non/existent/tokenizer.json");

        CrossEncoderService service = new CrossEncoderService(properties);

        // When
        service.initialize();

        // Then
        assertFalse(service.isAvailable());
    }

    @Test
    void shouldReturnEmptyArrayWhenNotAvailable() {
        // Given
        UserModelProperties properties = new UserModelProperties();
        properties.setCrossEncoderModelPath("/non/existent/model.onnx");
        properties.setCrossEncoderTokenizerPath("/non/existent/tokenizer.json");

        CrossEncoderService service = new CrossEncoderService(properties);
        service.initialize();

        // When
        float[] scores = service.score("test query", List.of("desc1", "desc2"));

        // Then
        assertEquals(0, scores.length);
    }

    @Test
    void shouldReturnEmptyArrayForEmptyDescriptions() {
        // Given
        UserModelProperties properties = new UserModelProperties();
        properties.setCrossEncoderModelPath("/non/existent/model.onnx");

        CrossEncoderService service = new CrossEncoderService(properties);
        service.initialize();

        // When
        float[] scores = service.score("test query", List.of());

        // Then
        assertEquals(0, scores.length);
    }
}
