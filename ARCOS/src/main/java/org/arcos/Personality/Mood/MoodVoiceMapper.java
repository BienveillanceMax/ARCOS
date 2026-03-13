package org.arcos.Personality.Mood;

import org.springframework.stereotype.Component;

@Component
public class MoodVoiceMapper {

    // --- Tunable coefficients (adjust during calibration) ---
    private static final float BASE_LENGTH = 1.05f;
    private static final float AROUSAL_LENGTH_COEFF = 0.35f;
    private static final float MIN_LENGTH = 0.7f;
    private static final float MAX_LENGTH = 1.4f;

    private static final float BASE_NOISE = 0.6f;
    private static final float PLEASURE_NOISE_COEFF = 0.3f;
    private static final float MIN_NOISE = 0.3f;
    private static final float MAX_NOISE = 0.9f;

    private static final float BASE_NOISEW = 0.8f;
    private static final float DOMINANCE_NOISEW_COEFF = 0.3f;
    private static final float MIN_NOISEW = 0.5f;
    private static final float MAX_NOISEW = 1.1f;

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
