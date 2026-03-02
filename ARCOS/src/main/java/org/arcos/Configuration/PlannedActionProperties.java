package org.arcos.Configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propriétés PlannedAction externalisées depuis application.properties.
 * Couvre le stockage, le pool de threads et les paramètres d'exécution.
 *
 * Préfixe : arcos.planned-action
 */
@Component
@ConfigurationProperties(prefix = "arcos.planned-action")
public class PlannedActionProperties {

    /** Chemin du fichier JSON de persistance des actions planifiées. */
    private String storagePath = "data/planned-actions.json";

    /** Chemin du fichier JSON de persistance de l'historique d'exécution. */
    private String historyStoragePath = "data/execution-history.json";

    /** Taille du pool de threads pour le scheduler d'actions planifiées. */
    private int threadPoolSize = 2;

    /** Nombre maximum de tentatives pour générer un plan d'exécution via LLM. */
    private int maxPlanRetries = 3;

    /** Nombre par défaut de résultats calendrier dans les plans d'exécution. */
    private int defaultCalendarMaxResults = 5;

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getHistoryStoragePath() {
        return historyStoragePath;
    }

    public void setHistoryStoragePath(String historyStoragePath) {
        this.historyStoragePath = historyStoragePath;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    public int getMaxPlanRetries() {
        return maxPlanRetries;
    }

    public void setMaxPlanRetries(int maxPlanRetries) {
        this.maxPlanRetries = maxPlanRetries;
    }

    public int getDefaultCalendarMaxResults() {
        return defaultCalendarMaxResults;
    }

    public void setDefaultCalendarMaxResults(int defaultCalendarMaxResults) {
        this.defaultCalendarMaxResults = defaultCalendarMaxResults;
    }
}
