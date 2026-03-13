package org.arcos.Personality.Mood;

import org.springframework.stereotype.Component;

@Component
public class MoodVoiceMapper {

    // --- Tunable coefficients (calibrated with GLaDOS fr_FR-medium model) ---
    private static final float BASE_LENGTH = 1.05f;
    private static final float AROUSAL_LENGTH_COEFF = 0.35f;
    private static final float MIN_LENGTH = 0.70f;
    private static final float MAX_LENGTH = 1.40f;

    private static final float BASE_NOISE = 0.50f;
    private static final float PLEASURE_NOISE_COEFF = 0.45f;
    private static final float MIN_NOISE = 0.25f;
    private static final float MAX_NOISE = 0.90f;

    private static final float BASE_NOISEW = 0.80f;
    private static final float DOMINANCE_NOISEW_COEFF = 0.45f;
    private static final float MIN_NOISEW = 0.50f;
    private static final float MAX_NOISEW = 1.10f;

    public static class VoiceParams {
        public final float lengthScale;
        public final float noiseScale;
        public final float noiseW;

        public VoiceParams(float lengthScale, float noiseScale, float noiseW) {
            this.lengthScale = lengthScale;
            this.noiseScale = noiseScale;
            this.noiseW = noiseW;
        }
    }

    public VoiceParams mapToVoice(PadState pad) {
        float lengthScale = clamp(
                BASE_LENGTH - (float)(pad.getArousal() * AROUSAL_LENGTH_COEFF),
                MIN_LENGTH, MAX_LENGTH);

        float noiseScale = clamp(
                BASE_NOISE - (float)(pad.getPleasure() * PLEASURE_NOISE_COEFF),
                MIN_NOISE, MAX_NOISE);

        float noiseW = clamp(
                BASE_NOISEW - (float)(pad.getDominance() * DOMINANCE_NOISEW_COEFF),
                MIN_NOISEW, MAX_NOISEW);

        return new VoiceParams(lengthScale, noiseScale, noiseW);
    }

    private float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }
}
