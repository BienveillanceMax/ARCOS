package org.arcos.Personality.Mood;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PadStateTest {

    @Test
    void testClampValues() {
        PadState state = new PadState(1.5, -2.0, 0.5);
        assertEquals(1.0, state.getPleasure());
        assertEquals(-1.0, state.getArousal());
        assertEquals(0.5, state.getDominance());
    }

    @Test
    void testUpdate() {
        PadState state = new PadState(0.0, 0.0, 0.0);
        state.update(0.5, 0.5, 0.5);
        assertEquals(0.5, state.getPleasure());
        assertEquals(0.5, state.getArousal());
        assertEquals(0.5, state.getDominance());

        state.update(1.0, 0.0, 0.0);
        assertEquals(1.0, state.getPleasure()); // Should clamp
    }

    @Test
    void testDistanceTo() {
        PadState s1 = new PadState(0.0, 0.0, 0.0);
        PadState s2 = new PadState(1.0, 0.0, 0.0);
        assertEquals(1.0, s1.distanceTo(s2));

        PadState s3 = new PadState(0.0, 1.0, 0.0);
        assertEquals(1.0, s1.distanceTo(s3));

        // Sqrt(1^2 + 1^2) = Sqrt(2) = 1.414...
        assertEquals(Math.sqrt(2), s2.distanceTo(s3), 0.0001);
    }
}
