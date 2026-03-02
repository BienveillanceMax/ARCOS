package org.arcos.Configuration;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

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

    // ── Homéostasie PAD ────────────────────────────────────────────────────────

    /** Facteur de décroissance exponentielle vers la baseline PAD par cycle (0.0–1.0). */
    private double moodDecayFactor = 0.95;

    /** Facteur d'ajustement du seuil d'initiative par le Plaisir PAD (0.0–1.0). */
    private double moodInitiativeFactor = 0.15;

    /** Poids de l'activation (Arousal) sur la volatilité des opinions (0.0–1.0). */
    private double moodOpinionActivationWeight = 0.5;

    /** Baselines PAD par profil (clé lowercase : calcifer, k2so, glados, default). */
    private Map<String, BaselinePad> moodBaseline = defaultBaselines();

    // ── Hyperparamètres d'opinions ────────────────────────────────────────────

    /** Hyperparamètres du modèle de mise à jour des opinions. */
    private OpinionParams opinion = new OpinionParams();

    // ── Classes imbriquées ────────────────────────────────────────────────────

    public static class BaselinePad {
        private double pleasure = 0.0;
        private double arousal = 0.0;
        private double dominance = 0.0;

        public double getPleasure() { return pleasure; }
        public void setPleasure(double pleasure) { this.pleasure = pleasure; }
        public double getArousal() { return arousal; }
        public void setArousal(double arousal) { this.arousal = arousal; }
        public double getDominance() { return dominance; }
        public void setDominance(double dominance) { this.dominance = dominance; }
    }

    public static class OpinionParams {
        private double weightExperience = 0.70;
        private double weightCoherence = 0.30;
        private double reinforceBase = 0.05;
        private double contradictBase = 0.08;
        private double stabilityGrowth = 0.02;
        private double stabilityShrink = 0.03;
        private double rhoNetwork = 0.25;

        public double getWeightExperience() { return weightExperience; }
        public void setWeightExperience(double weightExperience) { this.weightExperience = weightExperience; }
        public double getWeightCoherence() { return weightCoherence; }
        public void setWeightCoherence(double weightCoherence) { this.weightCoherence = weightCoherence; }
        public double getReinforceBase() { return reinforceBase; }
        public void setReinforceBase(double reinforceBase) { this.reinforceBase = reinforceBase; }
        public double getContradictBase() { return contradictBase; }
        public void setContradictBase(double contradictBase) { this.contradictBase = contradictBase; }
        public double getStabilityGrowth() { return stabilityGrowth; }
        public void setStabilityGrowth(double stabilityGrowth) { this.stabilityGrowth = stabilityGrowth; }
        public double getStabilityShrink() { return stabilityShrink; }
        public void setStabilityShrink(double stabilityShrink) { this.stabilityShrink = stabilityShrink; }
        public double getRhoNetwork() { return rhoNetwork; }
        public void setRhoNetwork(double rhoNetwork) { this.rhoNetwork = rhoNetwork; }
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @PostConstruct
    public void validate() {
        double sum = opinion.getWeightExperience() + opinion.getWeightCoherence();
        if (Math.abs(sum - 1.0) > 0.001) {
            throw new IllegalStateException(
                "arcos.personality.opinion.weight-experience + weight-coherence doit être égal à 1.0, valeur actuelle : " + sum);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Map<String, BaselinePad> defaultBaselines() {
        Map<String, BaselinePad> defaults = new HashMap<>();
        defaults.put("calcifer", padOf(0.3, 0.2, 0.1));
        defaults.put("k2so", padOf(-0.1, 0.1, 0.0));
        defaults.put("glados", padOf(-0.2, 0.0, 0.3));
        defaults.put("default", padOf(0.0, 0.0, 0.0));
        return defaults;
    }

    private static BaselinePad padOf(double p, double a, double d) {
        BaselinePad b = new BaselinePad();
        b.setPleasure(p);
        b.setArousal(a);
        b.setDominance(d);
        return b;
    }

    /**
     * Retourne la baseline PAD pour le profil actif.
     * Fallback sur (0,0,0) si le profil n'est pas défini.
     */
    public BaselinePad getMoodBaselineForProfile() {
        String key = profile != null ? profile.toLowerCase() : "default";
        return moodBaseline.getOrDefault(key, new BaselinePad());
    }

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

    public double getMoodDecayFactor() { return moodDecayFactor; }
    public void setMoodDecayFactor(double moodDecayFactor) { this.moodDecayFactor = moodDecayFactor; }

    public double getMoodInitiativeFactor() { return moodInitiativeFactor; }
    public void setMoodInitiativeFactor(double moodInitiativeFactor) { this.moodInitiativeFactor = moodInitiativeFactor; }

    public double getMoodOpinionActivationWeight() { return moodOpinionActivationWeight; }
    public void setMoodOpinionActivationWeight(double moodOpinionActivationWeight) { this.moodOpinionActivationWeight = moodOpinionActivationWeight; }

    public Map<String, BaselinePad> getMoodBaseline() { return moodBaseline; }
    public void setMoodBaseline(Map<String, BaselinePad> moodBaseline) { this.moodBaseline = moodBaseline; }

    public OpinionParams getOpinion() { return opinion; }
    public void setOpinion(OpinionParams opinion) { this.opinion = opinion; }
}
