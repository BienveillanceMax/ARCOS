package org.arcos.Configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propriétés de personnalité externalisées depuis application.properties.
 * Remplace toutes les constantes hardcodées dans DesireService, OpinionService,
 * et DesireInitiativeProducer.
 *
 * Préfixe : arcos.personality
 */
@Component
@ConfigurationProperties(prefix = "arcos.personality")
public class PersonalityProperties {

    /** Profil de personnalité prédéfini : CALCIFER, K2SO, GLADOS, DEFAULT. */
    private String profile = "DEFAULT";

    // ── Seuils d'opinion ───────────────────────────────────────────────────────

    /** Seuil d'intensité d'opinion pour la création d'opinion (0.0–1.0). */
    private double opinionThreshold = 0.5;

    /** Seuil de similarité cosinus pour regrouper deux opinions existantes (0.0–1.0). */
    private double opinionSimilarityThreshold = 0.85;

    /** TopK utilisé lors de la recherche d'opinions similaires. */
    private int opinionSearchTopk = 10;

    // ── Seuils de désir ────────────────────────────────────────────────────────

    /** Seuil de haute intensité de désir pour déclencher une initiative (0.0–1.0). */
    private double desireHighThreshold = 0.8;

    /** Seuil de basse intensité de désir (0.0–1.0). */
    private double desireLowThreshold = 0.3;

    /** Intensité minimale d'opinion pour créer un désir (0.0–1.0). */
    private double desireCreateThreshold = 0.5;

    /** Intensité minimale pour qu'un désir passe en statut PENDING (0.0–1.0). */
    private double desirePendingThreshold = 0.7;

    /** Facteur de lissage lors de la mise à jour de l'intensité d'un désir (0.0–1.0). */
    private double desireSmoothingFactor = 0.7;

    // ── Configuration des initiatives ─────────────────────────────────────────

    /** Intensité minimale d'un désir pour déclencher une initiative autonome (0.0–1.0). */
    private double initiativeThreshold = 0.8;

    /** Heure minimale (incluse) avant laquelle aucune initiative n'est déclenchée (0–23). */
    private int initiativeNoInitiativeUntilHour = 9;

    // ── Getters / Setters ──────────────────────────────────────────────────────

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public double getOpinionThreshold() {
        return opinionThreshold;
    }

    public void setOpinionThreshold(double opinionThreshold) {
        this.opinionThreshold = opinionThreshold;
    }

    public double getOpinionSimilarityThreshold() {
        return opinionSimilarityThreshold;
    }

    public void setOpinionSimilarityThreshold(double opinionSimilarityThreshold) {
        this.opinionSimilarityThreshold = opinionSimilarityThreshold;
    }

    public int getOpinionSearchTopk() {
        return opinionSearchTopk;
    }

    public void setOpinionSearchTopk(int opinionSearchTopk) {
        this.opinionSearchTopk = opinionSearchTopk;
    }

    public double getDesireHighThreshold() {
        return desireHighThreshold;
    }

    public void setDesireHighThreshold(double desireHighThreshold) {
        this.desireHighThreshold = desireHighThreshold;
    }

    public double getDesireLowThreshold() {
        return desireLowThreshold;
    }

    public void setDesireLowThreshold(double desireLowThreshold) {
        this.desireLowThreshold = desireLowThreshold;
    }

    public double getDesireCreateThreshold() {
        return desireCreateThreshold;
    }

    public void setDesireCreateThreshold(double desireCreateThreshold) {
        this.desireCreateThreshold = desireCreateThreshold;
    }

    public double getDesirePendingThreshold() {
        return desirePendingThreshold;
    }

    public void setDesirePendingThreshold(double desirePendingThreshold) {
        this.desirePendingThreshold = desirePendingThreshold;
    }

    public double getDesireSmoothingFactor() {
        return desireSmoothingFactor;
    }

    public void setDesireSmoothingFactor(double desireSmoothingFactor) {
        this.desireSmoothingFactor = desireSmoothingFactor;
    }

    public double getInitiativeThreshold() {
        return initiativeThreshold;
    }

    public void setInitiativeThreshold(double initiativeThreshold) {
        this.initiativeThreshold = initiativeThreshold;
    }

    public int getInitiativeNoInitiativeUntilHour() {
        return initiativeNoInitiativeUntilHour;
    }

    public void setInitiativeNoInitiativeUntilHour(int initiativeNoInitiativeUntilHour) {
        this.initiativeNoInitiativeUntilHour = initiativeNoInitiativeUntilHour;
    }
}
