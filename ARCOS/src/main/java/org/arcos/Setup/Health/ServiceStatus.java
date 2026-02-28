package org.arcos.Setup.Health;

/**
 * Statut d'un service externe tel que détecté par un health checker.
 */
public enum ServiceStatus {
    /** Service accessible et fonctionnel. */
    ONLINE,
    /** Service inaccessible ou en erreur. */
    OFFLINE,
    /** Service partiellement disponible (ex: Qdrant en cours de retry). */
    DEGRADED,
    /** Vérification en cours (non encore terminée). */
    CHECKING
}
