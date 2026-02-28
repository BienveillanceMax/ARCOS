package org.arcos.Setup.Health;

/**
 * Vérifie la disponibilité de Porcupine (wake word) en vérifiant la présence de la clé.
 * Pas de validation réseau possible sans le SDK Porcupine — vérifie seulement le format.
 */
public class PorcupineHealthChecker implements ServiceHealthCheck {

    @Override
    public String serviceName() {
        return "Porcupine";
    }

    @Override
    public HealthResult check(ServiceConfig config) {
        String apiKey = config.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return HealthResult.offline("PORCUPINE_ACCESS_KEY absent");
        }
        if (apiKey.length() < 20) {
            return HealthResult.offline("PORCUPINE_ACCESS_KEY trop courte — format invalide");
        }
        // La clé semble valide (validation réelle possible seulement à l'initialisation Porcupine)
        return HealthResult.online("Clé présente — validation complète au démarrage", 0);
    }
}
