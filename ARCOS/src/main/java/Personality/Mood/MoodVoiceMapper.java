package Personality.Mood;

import org.springframework.stereotype.Component;

@Component
public class MoodVoiceMapper {

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
        // Base values
        float baseLength = 1.0f;
        float baseNoise = 0.667f;
        float baseNoiseW = 0.8f;

        // Arousal affects Speed (Length Scale)
        // High Arousal -> Faster (lower length)
        // Low Arousal -> Slower (higher length)
        // Range: -1.0 to 1.0
        // If A=1.0 -> length = 0.8 (Fast)
        // If A=-1.0 -> length = 1.2 (Slow)
        float lengthScale = baseLength - (float)(pad.getArousal() * 0.2);

        // Pleasure affects Noise Scale? (Stability/Calmness)
        // High Pleasure -> Smoother?
        // This is experimental. Let's vary Noise Scale slightly with Arousal too.
        // High Energy -> More Noise
        float noiseScale = baseNoise + (float)(pad.getArousal() * 0.1);

        // Dominance affects NoiseW? (Pronunciation/Width)
        // High Dominance -> Clearer?
        float noiseW = baseNoiseW;

        // Clamp values to safe ranges
        lengthScale = clamp(lengthScale, 0.5f, 2.0f);
        noiseScale = clamp(noiseScale, 0.1f, 1.0f);

        return new VoiceParams(lengthScale, noiseScale, noiseW);
    }

    private float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }
}
