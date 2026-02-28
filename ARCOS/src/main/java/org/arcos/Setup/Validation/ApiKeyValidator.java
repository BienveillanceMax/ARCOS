package org.arcos.Setup.Validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Valide les clés API via des appels HTTP légers.
 * Utilise java.net.http.HttpClient (disponible Java 11+ — pas de dépendances externes).
 * Aucune dépendance Spring.
 */
public class ApiKeyValidator {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyValidator.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;

    public ApiKeyValidator() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    // Constructeur pour les tests (injection d'un client mocké)
    ApiKeyValidator(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Résultat d'une validation de clé API.
     */
    public record ValidationResult(boolean valid, String message) {
        public static ValidationResult ok() {
            return new ValidationResult(true, "Clé valide");
        }
        public static ValidationResult invalid(String reason) {
            return new ValidationResult(false, reason);
        }
    }

    /**
     * Valide une clé Mistral AI en appelant GET /v1/models.
     * HTTP 200 = valide, HTTP 401 = invalide, autres = erreur réseau.
     */
    public ValidationResult validateMistralKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return ValidationResult.invalid("Clé vide");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mistral.ai/v1/models"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json")
                    .timeout(TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status == 200) return ValidationResult.ok();
            if (status == 401) return ValidationResult.invalid("Clé refusée (HTTP 401 Unauthorized)");
            if (status == 429) return ValidationResult.invalid("Quota dépassé (HTTP 429 Too Many Requests)");
            return ValidationResult.invalid("Réponse inattendue : HTTP " + status);

        } catch (java.net.http.HttpTimeoutException e) {
            return ValidationResult.invalid("Délai dépassé (10s) — vérifier la connexion internet");
        } catch (Exception e) {
            log.debug("Erreur validation clé Mistral : {}", e.getMessage());
            return ValidationResult.invalid("Erreur réseau : " + e.getMessage());
        }
    }

    /**
     * Valide une clé Brave Search API en appelant GET /res/v1/web/search.
     * HTTP 200 ou 422 (requête invalide mais clé OK) = valide.
     */
    public ValidationResult validateBraveKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return ValidationResult.invalid("Clé vide");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.search.brave.com/res/v1/web/search?q=test"))
                    .header("Accept", "application/json")
                    .header("X-Subscription-Token", apiKey)
                    .timeout(TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status == 200 || status == 422) return ValidationResult.ok();
            if (status == 401) return ValidationResult.invalid("Clé refusée (HTTP 401 Unauthorized)");
            if (status == 429) return ValidationResult.invalid("Quota dépassé (HTTP 429 Too Many Requests)");
            return ValidationResult.invalid("Réponse inattendue : HTTP " + status);

        } catch (java.net.http.HttpTimeoutException e) {
            return ValidationResult.invalid("Délai dépassé (10s) — vérifier la connexion internet");
        } catch (Exception e) {
            log.debug("Erreur validation clé Brave : {}", e.getMessage());
            return ValidationResult.invalid("Erreur réseau : " + e.getMessage());
        }
    }

    /**
     * Vérifie qu'une clé Porcupine est non vide (pas de validation API disponible sans SDK).
     */
    public ValidationResult validatePorcupineKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return ValidationResult.invalid("Clé vide");
        }
        if (apiKey.length() < 20) {
            return ValidationResult.invalid("Clé trop courte — format attendu : chaîne base64 longue");
        }
        return ValidationResult.ok();
    }

    /**
     * Tronque une clé pour l'affichage sécurisé dans les logs.
     * Exemple : "sk-abc...XY42"
     */
    public static String maskKey(String key) {
        if (key == null || key.length() < 4) return "****";
        return "****" + key.substring(key.length() - 4);
    }
}
