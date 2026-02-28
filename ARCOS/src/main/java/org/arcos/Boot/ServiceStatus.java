package org.arcos.Boot;

/**
 * Statut d'un service au démarrage d'ARCOS.
 */
public enum ServiceStatus {
    /** Service accessible et fonctionnel. */
    ONLINE,
    /** Service inaccessible ou clé absente. */
    OFFLINE,
    /** Service partiellement disponible. */
    DEGRADED
}
