package org.arcos.Mood;

import org.arcos.Personality.Mood.MoodVoiceMapper;
import org.arcos.Personality.Mood.PadState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class MoodVoiceMapperTest {

    private final MoodVoiceMapper mapper = new MoodVoiceMapper();

    @Test
    void neutral_shouldReturnBaseValues() {
        PadState pad = new PadState(0.0, 0.0, 0.0);
        MoodVoiceMapper.VoiceParams params = mapper.mapToVoice(pad);

        assertEquals(1.05f, params.lengthScale, 0.01f, "Neutral arousal -> base speed");
        assertEquals(0.6f, params.noiseScale, 0.01f, "Neutral pleasure -> base noise");
        assertEquals(0.8f, params.noiseW, 0.01f, "Neutral dominance -> base noiseW");
    }

    @Test
    void joyful_shouldBeFastSmoothRegular() {
        // P=+0.8, A=+0.5, D=+0.3
        PadState pad = new PadState(0.8, 0.5, 0.3);
        MoodVoiceMapper.VoiceParams params = mapper.mapToVoice(pad);

        assertTrue(params.lengthScale < 1.05f, "High arousal -> faster than base");
        assertTrue(params.noiseScale < 0.6f, "High pleasure -> smoother than base");
        assertTrue(params.noiseW < 0.8f, "High dominance -> more regular than base");
    }

    @Test
    void angry_shouldBeVeryFastRoughVeryRegular() {
        // P=-0.5, A=+0.9, D=+0.7
        PadState pad = new PadState(-0.5, 0.9, 0.7);
        MoodVoiceMapper.VoiceParams params = mapper.mapToVoice(pad);

        assertTrue(params.lengthScale < 0.8f, "Very high arousal -> very fast");
        assertTrue(params.noiseScale > 0.6f, "Negative pleasure -> rougher than base");
        assertTrue(params.noiseW < 0.8f, "High dominance -> regular");
    }

    @Test
    void bored_shouldBeSlowSlightlyRoughVariable() {
        // P=-0.2, A=-0.8, D=-0.3
        PadState pad = new PadState(-0.2, -0.8, -0.3);
        MoodVoiceMapper.VoiceParams params = mapper.mapToVoice(pad);

        assertTrue(params.lengthScale > 1.2f, "Low arousal -> slow");
        assertTrue(params.noiseScale > 0.6f, "Negative pleasure -> rougher");
        assertTrue(params.noiseW > 0.8f, "Low dominance -> variable");
    }

    @Test
    void anxious_shouldBeFastRoughVeryVariable() {
        // P=-0.6, A=+0.6, D=-0.8
        PadState pad = new PadState(-0.6, 0.6, -0.8);
        MoodVoiceMapper.VoiceParams params = mapper.mapToVoice(pad);

        assertTrue(params.lengthScale < 1.05f, "High arousal -> fast");
        assertTrue(params.noiseScale > 0.6f, "Negative pleasure -> rough");
        assertTrue(params.noiseW > 0.8f, "Low dominance -> variable");
    }

    @ParameterizedTest
    @CsvSource({
        "1.0, 1.0, 1.0",
        "-1.0, -1.0, -1.0",
        "1.0, -1.0, 1.0",
        "-1.0, 1.0, -1.0"
    })
    void extremeValues_shouldStayWithinClampedRanges(double p, double a, double d) {
        PadState pad = new PadState(p, a, d);
        MoodVoiceMapper.VoiceParams params = mapper.mapToVoice(pad);

        assertTrue(params.lengthScale >= 0.7f && params.lengthScale <= 1.4f,
                "lengthScale " + params.lengthScale + " out of range [0.7, 1.4]");
        assertTrue(params.noiseScale >= 0.3f && params.noiseScale <= 0.9f,
                "noiseScale " + params.noiseScale + " out of range [0.3, 0.9]");
        assertTrue(params.noiseW >= 0.5f && params.noiseW <= 1.1f,
                "noiseW " + params.noiseW + " out of range [0.5, 1.1]");
    }

    @Test
    void allMoodStates_shouldProduceDistinctParams() {
        MoodVoiceMapper.VoiceParams joyful = mapper.mapToVoice(new PadState(0.8, 0.5, 0.3));
        MoodVoiceMapper.VoiceParams angry = mapper.mapToVoice(new PadState(-0.5, 0.9, 0.7));
        MoodVoiceMapper.VoiceParams bored = mapper.mapToVoice(new PadState(-0.2, -0.8, -0.3));

        // At least one param should differ meaningfully (> 0.1) between each pair
        assertTrue(
            Math.abs(joyful.lengthScale - angry.lengthScale) > 0.1f ||
            Math.abs(joyful.noiseScale - angry.noiseScale) > 0.1f ||
            Math.abs(joyful.noiseW - angry.noiseW) > 0.1f,
            "Joyful and Angry should sound distinct"
        );
        assertTrue(
            Math.abs(joyful.lengthScale - bored.lengthScale) > 0.1f ||
            Math.abs(joyful.noiseScale - bored.noiseScale) > 0.1f ||
            Math.abs(joyful.noiseW - bored.noiseW) > 0.1f,
            "Joyful and Bored should sound distinct"
        );
    }
}
