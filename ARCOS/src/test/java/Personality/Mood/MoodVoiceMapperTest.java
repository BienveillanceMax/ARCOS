package Personality.Mood;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MoodVoiceMapperTest {

    private final MoodVoiceMapper mapper = new MoodVoiceMapper();

    @Test
    void testMapToVoice_HighArousal_ShouldBeFast() {
        // High Arousal (1.0) -> Faster speed (lower lengthScale)
        PadState pad = new PadState(0.0, 1.0, 0.0);
        MoodVoiceMapper.VoiceParams params = mapper.mapToVoice(pad);

        // Base 1.0 - (1.0 * 0.2) = 0.8
        assertEquals(0.8f, params.lengthScale, 0.01f);
    }

    @Test
    void testMapToVoice_LowArousal_ShouldBeSlow() {
        // Low Arousal (-1.0) -> Slower speed (higher lengthScale)
        PadState pad = new PadState(0.0, -1.0, 0.0);
        MoodVoiceMapper.VoiceParams params = mapper.mapToVoice(pad);

        // Base 1.0 - (-1.0 * 0.2) = 1.2
        assertEquals(1.2f, params.lengthScale, 0.01f);
    }

    @Test
    void testMapToVoice_Neutral() {
        PadState pad = new PadState(0.0, 0.0, 0.0);
        MoodVoiceMapper.VoiceParams params = mapper.mapToVoice(pad);

        assertEquals(1.0f, params.lengthScale, 0.01f);
        assertEquals(0.667f, params.noiseScale, 0.01f);
        assertEquals(0.8f, params.noiseW, 0.01f);
    }
}
