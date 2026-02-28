package org.arcos.Configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propriétés de personnalité externalisées depuis application.properties.
 * Remplace toutes les constantes hardcodées dans DesireService et OpinionService.
 *
 * Préfixe : arcos.personality
 */
@Component
@ConfigurationProperties(prefix = "arcos.personality")
public class PersonalityProperties {

    /** Profil de personnalité prédéfini : CALCIFER, K2SO, GLADOS, DEFAULT. */
    private String profile = "DEFAULT";

    /** Seuil d'intensité d'opinion pour la création d'opinion (0.0–1.0). */
    private double opinionThreshold = 0.5;

    /** Seuil de haute intensité de désir pour déclencher une initiative (0.0–1.0). */
    private double desireHighThreshold = 0.8;

    /** Seuil de basse intensité de désir (0.0–1.0). */
    private double desireLowThreshold = 0.3;

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
}
