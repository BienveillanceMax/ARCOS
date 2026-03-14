package org.arcos.UnitTests.UserModel.BatchPipeline;

import org.arcos.UserModel.BatchPipeline.IdleDetectionService;
import org.arcos.UserModel.UserModelProperties;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class IdleDetectionServiceTest {

    @Test
    void isIdle_returnsFalseImmediatelyAfterRecordInteraction() {
        // Given: a service with 30 min threshold
        UserModelProperties properties = new UserModelProperties();
        properties.setIdleThresholdMinutes(30);
        IdleDetectionService service = new IdleDetectionService(properties);

        // When
        service.recordInteraction();

        // Then
        assertFalse(service.isIdle());
    }

    @Test
    void isIdle_returnsTrueAfterThresholdPasses() throws Exception {
        // Given: a service with 1 min threshold
        UserModelProperties properties = new UserModelProperties();
        properties.setIdleThresholdMinutes(1);
        IdleDetectionService service = new IdleDetectionService(properties);

        // Simulate that the last interaction was 2 minutes ago using reflection
        Field field = IdleDetectionService.class.getDeclaredField("lastInteractionTime");
        field.setAccessible(true);
        field.set(service, LocalDateTime.now().minusMinutes(2));

        // When/Then
        assertTrue(service.isIdle());
    }

    @Test
    void getIdleDuration_returnsPositiveDuration() {
        // Given
        UserModelProperties properties = new UserModelProperties();
        properties.setIdleThresholdMinutes(30);
        IdleDetectionService service = new IdleDetectionService(properties);

        // When
        var duration = service.getIdleDuration();

        // Then
        assertNotNull(duration);
        assertFalse(duration.isNegative());
    }
}
