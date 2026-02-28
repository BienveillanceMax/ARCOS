package org.arcos.Configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propriétés Qdrant externalisées depuis application.properties.
 * Couvre la dimension d'embedding, la métrique de distance et la stratégie de retry.
 *
 * Préfixe : arcos.qdrant
 */
@Component
@ConfigurationProperties(prefix = "arcos.qdrant")
public class QdrantProperties {

    /** Dimension des vecteurs d'embedding (doit correspondre au modèle d'embedding utilisé). */
    private int embeddingDimension = 1024;

    /** Métrique de distance Qdrant : COSINE, EUCLID, DOT, MANHATTAN. */
    private String distanceMetric = "COSINE";

    /** Nombre maximum de tentatives de connexion à Qdrant au démarrage. */
    private int maxRetries = 6;

    /** Délai initial entre deux tentatives de connexion (ms). */
    private long initialBackoffMs = 1_000;

    /** Délai maximum entre deux tentatives de connexion (ms). */
    private long maxBackoffMs = 30_000;

    public int getEmbeddingDimension() {
        return embeddingDimension;
    }

    public void setEmbeddingDimension(int embeddingDimension) {
        this.embeddingDimension = embeddingDimension;
    }

    public String getDistanceMetric() {
        return distanceMetric;
    }

    public void setDistanceMetric(String distanceMetric) {
        this.distanceMetric = distanceMetric;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public long getInitialBackoffMs() {
        return initialBackoffMs;
    }

    public void setInitialBackoffMs(long initialBackoffMs) {
        this.initialBackoffMs = initialBackoffMs;
    }

    public long getMaxBackoffMs() {
        return maxBackoffMs;
    }

    public void setMaxBackoffMs(long maxBackoffMs) {
        this.maxBackoffMs = maxBackoffMs;
    }
}
