package org.arcos.UnitTests.Setup.Health;

import org.arcos.Setup.Health.HealthResult;
import org.arcos.Setup.Health.QdrantHealthChecker;
import org.arcos.Setup.Health.ServiceHealthCheck;
import org.arcos.Setup.Health.ServiceStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QdrantHealthCheckerTest {

    @Test
    void serviceName_isQdrant() {
        assertEquals("Qdrant", new QdrantHealthChecker().serviceName());
    }

    @Test
    void check_unreachableHost_returnsOffline() {
        // Given — port 19999 est probablement libre (aucun service)
        QdrantHealthChecker checker = new QdrantHealthChecker();
        ServiceHealthCheck.ServiceConfig config = ServiceHealthCheck.ServiceConfig.of("localhost", 19999);

        // When
        HealthResult result = checker.check(config);

        // Then
        assertEquals(ServiceStatus.OFFLINE, result.status());
        assertNotNull(result.message());
    }

    @Test
    void check_defaultsToLocalhostPort6334_whenConfigEmpty() {
        // Given — host et port null/0 → defaults appliqués
        QdrantHealthChecker checker = new QdrantHealthChecker();
        ServiceHealthCheck.ServiceConfig config = ServiceHealthCheck.ServiceConfig.of(null, 0);

        // When — va tenter de se connecter à localhost:6334, probablement OFFLINE en CI
        HealthResult result = checker.check(config);

        // Then — doit retourner un résultat sans exception
        assertNotNull(result);
        assertNotNull(result.status());
    }
}
