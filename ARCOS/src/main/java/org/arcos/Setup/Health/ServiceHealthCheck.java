package org.arcos.Setup.Health;

/**
 * Interface pour les health checkers de services externes.
 * Utilisable en contexte pre-Spring (wizard) et post-Spring (rapport de boot).
 * Aucune dépendance Spring.
 */
public interface ServiceHealthCheck {

    /** Nom d'affichage du service (ex: "Qdrant", "Mistral AI"). */
    String serviceName();

    /**
     * Vérifie la disponibilité du service.
     *
     * @param config configuration du service (host, port, clé API)
     * @return résultat du check avec statut et message
     */
    HealthResult check(ServiceConfig config);

    /**
     * Configuration d'un service pour le health check.
     */
    record ServiceConfig(String host, int port, String apiKey) {

        public static ServiceConfig of(String host, int port) {
            return new ServiceConfig(host, port, null);
        }

        public static ServiceConfig withKey(String apiKey) {
            return new ServiceConfig(null, -1, apiKey);
        }
    }
}
