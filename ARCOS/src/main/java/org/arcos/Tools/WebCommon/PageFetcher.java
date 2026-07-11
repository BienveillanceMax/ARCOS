package org.arcos.Tools.WebCommon;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Téléchargement HTTP d'une page web : suit les redirections manuellement
 * (garde SSRF re-validée à chaque hop) et refuse les contenus non textuels.
 */
@Slf4j
@Component
public class PageFetcher {

    private static final int MAX_REDIRECTS = 5;

    private final HttpClient httpClient;
    private final SsrfGuard ssrfGuard;

    public record FetchedPage(String finalUrl, String contentType, String html) {}

    @Autowired
    public PageFetcher(SsrfGuard ssrfGuard) {
        this(HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .followRedirects(HttpClient.Redirect.NEVER) // SSRF: validate every hop ourselves
                        .build(),
                ssrfGuard);
    }

    public PageFetcher(HttpClient httpClient, SsrfGuard ssrfGuard) {
        this.httpClient = httpClient;
        this.ssrfGuard = ssrfGuard;
    }

    @CircuitBreaker(name = "webPage")
    public FetchedPage fetch(String url, int timeoutSeconds) throws IOException, InterruptedException {
        URI uri = URI.create(url);
        HttpResponse<String> response = null;

        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            ssrfGuard.assertHostAllowed(uri); // re-validate after each redirect

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("User-Agent", "ARCOS/1.0")
                    .header("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.5")
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .GET()
                    .build();

            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("Location").orElse(null);
                if (location == null) break;
                uri = uri.resolve(location); // resolve relative redirects
                continue;
            }
            break;
        }

        int finalStatus = response.statusCode();
        if (finalStatus >= 300 && finalStatus < 400) {
            // Redirect budget exhausted — do NOT fall through and parse the 3xx body as page content.
            throw new IOException("Trop de redirections (max " + MAX_REDIRECTS + ") pour " + url);
        }
        if (finalStatus >= 400) {
            throw new IOException("HTTP " + finalStatus + " pour " + url);
        }

        String contentType = response.headers().firstValue("Content-Type").orElse("");
        if (!contentType.isBlank() && !isSupportedContentType(contentType)) {
            throw new IOException("Type de contenu non supporté : " + contentType);
        }

        return new FetchedPage(uri.toString(), contentType, response.body());
    }

    private static boolean isSupportedContentType(String contentType) {
        String normalized = contentType.toLowerCase();
        return normalized.contains("text/html")
                || normalized.contains("text/plain")
                || normalized.contains("application/xhtml");
    }
}
