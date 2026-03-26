package org.arcos.Personality.Mood;

import org.arcos.Configuration.PersonalityProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MoodService {

    private final MoodStateHolder moodStateHolder;
    private final PersonalityProperties personalityProperties;

    @Autowired
    public MoodService(MoodStateHolder moodStateHolder, PersonalityProperties personalityProperties) {
        this.moodStateHolder = moodStateHolder;
        this.personalityProperties = personalityProperties;
    }

    public void applyMoodUpdate(MoodUpdate update) {
        if (update == null) return;
        try {
            PadState currentPad = moodStateHolder.getPadState();
            currentPad.update(update.deltaPleasure, update.deltaArousal, update.deltaDominance);
            moodStateHolder.setPadState(currentPad);

            log.info("Mood updated: {} (Delta: P={}, A={}, D={}) | Reasoning: {}",
                Mood.fromPadState(currentPad),
                update.deltaPleasure, update.deltaArousal, update.deltaDominance,
                update.reasoning);

        } catch (Exception e) {
            log.error("Failed to apply mood update", e);
        }
    }

    public Mood getCurrentMood() {
        return Mood.fromPadState(moodStateHolder.getPadState());
    }

    /**
     * Décroissance exponentielle de l'état PAD vers la baseline du profil actif.
     * Formule : new_val = current * f + baseline * (1 - f)
     * Exécutée automatiquement à interval configurable.
     */
    @Scheduled(fixedRateString = "${arcos.personality.mood-decay-interval-ms:3600000}")
    public void applyDecay() {
        try {
            PadState current = moodStateHolder.getPadState();
            PersonalityProperties.BaselinePad baseline = personalityProperties.getMoodBaselineForProfile();
            double f = personalityProperties.getMoodDecayFactor();

            PadState decayed = new PadState(
                current.getPleasure() * f + baseline.getPleasure() * (1 - f),
                current.getArousal() * f + baseline.getArousal() * (1 - f),
                current.getDominance() * f + baseline.getDominance() * (1 - f)
            );
            moodStateHolder.setPadState(decayed);
            log.debug("{\"event\":\"mood_decay\",\"before\":\"{}\",\"after\":\"{}\",\"decay_factor\":{}}",
                current, decayed, f);
        } catch (Exception e) {
            log.error("Erreur lors de la décroissance PAD", e);
        }
    }

    /**
     * Retourne le seuil d'initiative ajusté par l'humeur (axe Plaisir).
     * Plaisir positif → seuil plus bas (plus proactif).
     * Plaisir négatif → seuil plus élevé (plus réservé).
     */
    public double getEffectiveInitiativeThreshold(double baseThreshold) {
        double pleasure = moodStateHolder.getPadState().getPleasure();
        double factor = personalityProperties.getMoodInitiativeFactor();
        return baseThreshold * (1.0 - pleasure * factor);
    }

    /**
     * Retourne le facteur de volatilité des opinions basé sur l'Activation (Arousal).
     * Arousal élevé → volatilité accrue.
     */
    public double getMoodOpinionVolatilityFactor() {
        double arousal = moodStateHolder.getPadState().getArousal();
        double weight = personalityProperties.getMoodOpinionActivationWeight();
        return 1.0 + Math.max(0.0, arousal) * weight;
    }
}
