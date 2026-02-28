package org.arcos.UnitTests.Setup.Health;

import org.arcos.Setup.Health.HealthResult;
import org.arcos.Setup.Health.PorcupineHealthChecker;
import org.arcos.Setup.Health.ServiceHealthCheck;
import org.arcos.Setup.Health.ServiceStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PorcupineHealthCheckerTest {

    @Test
    void serviceName_isPorcupine() {
        assertEquals("Porcupine", new PorcupineHealthChecker().serviceName());
    }

    @Test
    void check_nullKey_returnsOffline() {
        // Given
        PorcupineHealthChecker checker = new PorcupineHealthChecker();
        ServiceHealthCheck.ServiceConfig config = ServiceHealthCheck.ServiceConfig.withKey(null);

        // When
        HealthResult result = checker.check(config);

        // Then
        assertEquals(ServiceStatus.OFFLINE, result.status());
        assertTrue(result.message().contains("absent"));
    }

    @Test
    void check_emptyKey_returnsOffline() {
        // Given
        PorcupineHealthChecker checker = new PorcupineHealthChecker();
        ServiceHealthCheck.ServiceConfig config = ServiceHealthCheck.ServiceConfig.withKey("");

        // When
        HealthResult result = checker.check(config);

        // Then
        assertEquals(ServiceStatus.OFFLINE, result.status());
    }

    @Test
    void check_shortKey_returnsOffline() {
        // Given
        PorcupineHealthChecker checker = new PorcupineHealthChecker();
        ServiceHealthCheck.ServiceConfig config = ServiceHealthCheck.ServiceConfig.withKey("short");

        // When
        HealthResult result = checker.check(config);

        // Then
        assertEquals(ServiceStatus.OFFLINE, result.status());
        assertTrue(result.message().contains("courte"));
    }

    @Test
    void check_validLengthKey_returnsOnline() {
        // Given
        PorcupineHealthChecker checker = new PorcupineHealthChecker();
        String longKey = "A".repeat(30);
        ServiceHealthCheck.ServiceConfig config = ServiceHealthCheck.ServiceConfig.withKey(longKey);

        // When
        HealthResult result = checker.check(config);

        // Then
        assertEquals(ServiceStatus.ONLINE, result.status());
    }
}
