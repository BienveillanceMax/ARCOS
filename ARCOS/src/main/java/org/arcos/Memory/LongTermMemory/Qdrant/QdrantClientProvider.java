package org.arcos.Memory.LongTermMemory.Qdrant;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.arcos.Configuration.QdrantProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@Getter
public class QdrantClientProvider {

    private final QdrantClient client;

    @Autowired
    public QdrantClientProvider(
            @Value("${qdrant.host}") String host,
            @Value("${qdrant.port}") int port,
            QdrantProperties qdrantProperties
    ) {
        this.client = createClientWithRetry(host, port,
                qdrantProperties.getMaxRetries(),
                qdrantProperties.getInitialBackoffMs(),
                qdrantProperties.getMaxBackoffMs());
    }

    // Constructeur package-private pour les tests, permettant de réduire les délais de retry
    QdrantClientProvider(String host, int port, int maxRetries, long initialBackoffMs, long maxBackoffMs) {
        this.client = createClientWithRetry(host, port, maxRetries, initialBackoffMs, maxBackoffMs);
    }

    private QdrantClient createClientWithRetry(String host, int port, int maxRetries, long initialBackoffMs, long maxBackoffMs) {
        long backoffMs = initialBackoffMs;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("Tentative de connexion à Qdrant {}:{} (tentative {}/{})", host, port, attempt, maxRetries);
                QdrantGrpcClient grpcClient = QdrantGrpcClient.newBuilder(host, port, false).build();
                QdrantClient client = new QdrantClient(grpcClient);
                // Vérification de la connexion avec un appel léger
                client.healthCheckAsync().get(5, TimeUnit.SECONDS);
                log.info("Connexion à Qdrant établie avec succès (tentative {}/{}).", attempt, maxRetries);
                return client;
            } catch (Exception e) {
                lastException = e;
                log.warn("Connexion Qdrant échouée (tentative {}/{}) : {}. Nouvel essai dans {}ms.",
                        attempt, maxRetries, e.getMessage(), backoffMs);
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted lors du retry Qdrant", ie);
                    }
                    backoffMs = Math.min(backoffMs * 2, maxBackoffMs);
                }
            }
        }
        throw new RuntimeException(
                String.format("Impossible de se connecter à Qdrant %s:%d après %d tentatives.", host, port, maxRetries),
                lastException
        );
    }
}
