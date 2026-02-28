package org.arcos.Setup.Health;

/**
 * Résultat d'un health check sur un service externe.
 */
public record HealthResult(ServiceStatus status, String message, long responseTimeMs) {

    public static HealthResult online(String message, long responseTimeMs) {
        return new HealthResult(ServiceStatus.ONLINE, message, responseTimeMs);
    }

    public static HealthResult offline(String message) {
        return new HealthResult(ServiceStatus.OFFLINE, message, -1);
    }

    public static HealthResult degraded(String message, long responseTimeMs) {
        return new HealthResult(ServiceStatus.DEGRADED, message, responseTimeMs);
    }

    public boolean isOnline() {
        return status == ServiceStatus.ONLINE;
    }
}
