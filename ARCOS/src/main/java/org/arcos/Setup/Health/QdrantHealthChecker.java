package org.arcos.Setup.Health;

import java.net.Socket;
import java.time.Duration;

/**
 * Vérifie la disponibilité de Qdrant via une connexion TCP au port gRPC.
 * N'utilise pas le client gRPC complet pour rester léger et sans dépendance Spring.
 */
public class QdrantHealthChecker implements ServiceHealthCheck {

    private static final int TIMEOUT_MS = 5000;

    @Override
    public String serviceName() {
        return "Qdrant";
    }

    @Override
    public HealthResult check(ServiceConfig config) {
        String host = config.host() != null ? config.host() : "localhost";
        int port = config.port() > 0 ? config.port() : 6334;

        long start = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), TIMEOUT_MS);
            long elapsed = System.currentTimeMillis() - start;
            return HealthResult.online(host + ":" + port, elapsed);
        } catch (Exception e) {
            return HealthResult.offline("Inaccessible (" + host + ":" + port + ") : " + simplify(e.getMessage()));
        }
    }

    private String simplify(String msg) {
        if (msg == null) return "erreur inconnue";
        if (msg.contains("Connection refused")) return "connexion refusée";
        if (msg.contains("timed out") || msg.contains("timeout")) return "délai dépassé";
        return msg;
    }
}
