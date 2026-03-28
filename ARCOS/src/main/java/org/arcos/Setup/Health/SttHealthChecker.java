package org.arcos.Setup.Health;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Vérifie la disponibilité du service speech-to-text via GET /health ou une connexion TCP.
 */
public class SttHealthChecker implements ServiceHealthCheck {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    @Override
    public String serviceName() {
        return "speech-to-text";
    }

    @Override
    public HealthResult check(ServiceConfig config) {
        String host = config.host() != null ? config.host() : "localhost";
        int port = config.port();
        if (port <= 0) {
            return HealthResult.offline("No port configured for STT health check");
        }

        long start = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + host + ":" + port + "/health"))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();

            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            long elapsed = System.currentTimeMillis() - start;

            // Any HTTP response means the service is running
            return HealthResult.online(host + ":" + port, elapsed);

        } catch (Exception e) {
            return tryTcpConnect(host, port, System.currentTimeMillis() - start);
        }
    }

    private HealthResult tryTcpConnect(String host, int port, long elapsed) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3000);
            return HealthResult.online(host + ":" + port + " (TCP)", elapsed);
        } catch (Exception e) {
            return HealthResult.offline("Inaccessible (" + host + ":" + port + ")");
        }
    }
}
