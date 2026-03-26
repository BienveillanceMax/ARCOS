package org.arcos.Mood;

import org.arcos.Personality.Mood.Mood;
import org.arcos.Personality.Mood.PadState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MoodTest {

    @Test
    void testFromPadState_ExactMatches() {
        assertEquals(Mood.JOIE, Mood.fromPadState(new PadState(0.8, 0.6, 0.5)));
        assertEquals(Mood.SURPRISE, Mood.fromPadState(new PadState(0.4, 0.8, -0.2)));
        assertEquals(Mood.MEPRIS, Mood.fromPadState(new PadState(-0.6, 0.5, 0.8)));
        assertEquals(Mood.HONTE, Mood.fromPadState(new PadState(-0.7, 0.4, -0.7)));
    }

    @Test
    void testFromPadState_ApproximateMatches() {
        // Close to Joy but slightly less aroused
        assertEquals(Mood.JOIE, Mood.fromPadState(new PadState(0.7, 0.5, 0.5)));

        // Neutral center
        assertEquals(Mood.NEUTRE, Mood.fromPadState(new PadState(0.0, 0.0, 0.0)));
    }

    // ── Gaps comblés — Sprint 8, Story 1.12 ─────────────────────────────────

    @Test
    void testFromPadState_ExtremeValues_ShouldNotThrow() {
        // Extreme corners of PAD space — should return a valid Mood
        assertNotNull(Mood.fromPadState(new PadState(-1.0, -1.0, -1.0)));
        assertNotNull(Mood.fromPadState(new PadState(1.0, 1.0, 1.0)));
    }

    @Test
    void testFromPadState_EachCenter_ShouldReturnMatchingMood() {
        for (Mood mood : Mood.values()) {
            assertEquals(mood, Mood.fromPadState(mood.getCenter()),
                    "fromPadState(center) should return " + mood.name());
        }
    }
}
