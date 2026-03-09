package org.arcos.Setup.Health;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Vérifie la disponibilité du service faster-whisper via GET /health ou une connexion TCP.
 */
public class FasterWhisperHealthChecker implements ServiceHealthCheck {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Override
    public String serviceName() {
        return "faster-whisper";
    }

    @Override
    public HealthResult check(ServiceConfig config) {
        String host = config.host() != null ? config.host() : "localhost";
        int port = config.port() > 0 ? config.port() : 9876;

        long start = System.currentTimeMillis();
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + host + ":" + port + "/health"))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            long elapsed = System.currentTimeMillis() - start;

            // Any HTTP response means the service is running (the Flask app
            // may not expose /health — a 404 still proves it's alive)
            return HealthResult.online(host + ":" + port, elapsed);

        } catch (Exception e) {
            // Fallback : simple connexion TCP
            return tryTcpConnect(host, port, System.currentTimeMillis() - start, e);
        }
    }

    private HealthResult tryTcpConnect(String host, int port, long elapsed, Exception originalError) {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), 3000);
            return HealthResult.online(host + ":" + port + " (TCP)", elapsed);
        } catch (Exception e) {
            return HealthResult.offline("Inaccessible (" + host + ":" + port + ")");
        }
    }
}
