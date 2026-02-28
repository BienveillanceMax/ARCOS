package org.arcos.Setup.Health;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Vérifie la disponibilité de l'API Mistral AI via GET /v1/models.
 */
public class MistralHealthChecker implements ServiceHealthCheck {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String MODELS_URL = "https://api.mistral.ai/v1/models";

    @Override
    public String serviceName() {
        return "Mistral AI";
    }

    @Override
    public HealthResult check(ServiceConfig config) {
        String apiKey = config.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return HealthResult.offline("MISTRALAI_API_KEY absent");
        }

        long start = System.currentTimeMillis();
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MODELS_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            long elapsed = System.currentTimeMillis() - start;
            int status = response.statusCode();

            if (status == 200) return HealthResult.online("api.mistral.ai (" + elapsed + "ms)", elapsed);
            if (status == 401) return HealthResult.offline("Clé invalide (HTTP 401)");
            return HealthResult.offline("HTTP " + status);

        } catch (Exception e) {
            return HealthResult.offline("Erreur réseau : " + simplify(e.getMessage()));
        }
    }

    private String simplify(String msg) {
        if (msg == null) return "erreur inconnue";
        if (msg.contains("timed out") || msg.contains("timeout")) return "délai dépassé (10s)";
        if (msg.contains("UnknownHost") || msg.contains("unknown host")) return "DNS introuvable";
        return msg;
    }
}
