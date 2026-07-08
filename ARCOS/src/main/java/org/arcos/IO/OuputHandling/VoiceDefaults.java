package org.arcos.IO.OuputHandling;

/**
 * Paramètres de voix Piper par défaut (alignés sur la config du modèle GLaDOS medium).
 * Source unique — utilisés par les surcharges sans humeur de {@link TTSModule} ;
 * MoodVoiceMapper module autour de ces valeurs.
 */
public final class VoiceDefaults {

    public static final float LENGTH_SCALE = 1.0f;
    public static final float NOISE_SCALE = 0.667f;
    public static final float NOISE_W = 0.8f;

    private VoiceDefaults() { }
}
