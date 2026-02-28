package org.arcos.UnitTests.Setup.Health;

import org.arcos.Setup.Health.HealthResult;
import org.arcos.Setup.Health.ServiceStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HealthResultTest {

    @Test
    void online_createsOnlineResult() {
        // When
        HealthResult result = HealthResult.online("localhost:6334", 42L);

        // Then
        assertEquals(ServiceStatus.ONLINE, result.status());
        assertEquals("localhost:6334", result.message());
        assertEquals(42L, result.responseTimeMs());
        assertTrue(result.isOnline());
    }

    @Test
    void offline_createsOfflineResult() {
        // When
        HealthResult result = HealthResult.offline("connexion refusée");

        // Then
        assertEquals(ServiceStatus.OFFLINE, result.status());
        assertEquals("connexion refusée", result.message());
        assertEquals(-1L, result.responseTimeMs());
        assertFalse(result.isOnline());
    }

    @Test
    void degraded_createsDegradedResult() {
        // When
        HealthResult result = HealthResult.degraded("partiellement disponible", 100L);

        // Then
        assertEquals(ServiceStatus.DEGRADED, result.status());
        assertFalse(result.isOnline());
    }
}
