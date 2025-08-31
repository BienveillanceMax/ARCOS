package Personality.Values;

import Personality.Values.Entities.DimensionSchwartz;
import Personality.Values.Entities.ValueSchwartz;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ValueProfileTest {

    private ValueProfile valueProfile;

    @BeforeEach
    void setUp() {
        valueProfile = new ValueProfile();
    }

    @Test
    void testInitialScores() {
        for (ValueSchwartz v : ValueSchwartz.values()) {
            assertEquals(50.0, valueProfile.getScore(v), 0.01);
        }
    }

    @Test
    void testSetAndGetScore() {
        valueProfile.setScore(ValueSchwartz.ACHIEVEMENT, 80.0);
        assertEquals(80.0, valueProfile.getScore(ValueSchwartz.ACHIEVEMENT), 0.01);
    }

    @Test
    void testSetScoreThrowsExceptionForInvalidScore() {
        assertThrows(IllegalArgumentException.class, () -> valueProfile.setScore(ValueSchwartz.ACHIEVEMENT, -10.0));
        assertThrows(IllegalArgumentException.class, () -> valueProfile.setScore(ValueSchwartz.ACHIEVEMENT, 110.0));
    }

    @Test
    void testAverageByDimension() {
        valueProfile.setScore(ValueSchwartz.SELF_DIRECTION_THOUGHT, 60.0);
        valueProfile.setScore(ValueSchwartz.SELF_DIRECTION_ACTION, 70.0);
        valueProfile.setScore(ValueSchwartz.STIMULATION, 80.0);
        valueProfile.setScore(ValueSchwartz.HEDONISM, 90.0);

        double expectedAverage = (60.0 + 70.0 + 80.0 + 90.0) / 4.0;
        assertEquals(expectedAverage, valueProfile.averageByDimension(DimensionSchwartz.OPENNESS_TO_CHANGE), 0.01);
    }

    @Test
    void testDimensionAverage() {
        // With default values, the average should be 50
        assertEquals(50.0, valueProfile.dimensionAverage(), 0.01);

        // Change some values
        valueProfile.setScore(ValueSchwartz.ACHIEVEMENT, 100.0);
        valueProfile.setScore(ValueSchwartz.POWER_DOMINANCE, 0.0);

        double total = 0;
        for (ValueSchwartz v : ValueSchwartz.values()) {
            total += valueProfile.getScore(v);
        }
        assertEquals(total / ValueSchwartz.values().length, valueProfile.dimensionAverage(), 0.01);
    }


    @Test
    void testNormalizeSumToOne() {
        valueProfile.setScore(ValueSchwartz.ACHIEVEMENT, 100.0);
        valueProfile.setScore(ValueSchwartz.POWER_DOMINANCE, 50.0);
        Map<ValueSchwartz, Double> normalized = valueProfile.normalizeSumToOne();
        double sum = normalized.values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, sum, 0.00001);
    }

    @Test
    void testConflictingValues() {
        valueProfile.setScore(ValueSchwartz.SECURITY_PERSONAL, 80.0);
        valueProfile.setScore(ValueSchwartz.SECURITY_SOCIAL, 80.0);
        List<ValueSchwartz> conflicts = valueProfile.conflictingValues(ValueSchwartz.SELF_DIRECTION_THOUGHT);
        assertTrue(conflicts.contains(ValueSchwartz.SECURITY_PERSONAL));
        assertTrue(conflicts.contains(ValueSchwartz.SECURITY_SOCIAL));
    }

    @Test
    void testGetSuppressedValues() {
        valueProfile.setScore(ValueSchwartz.ACHIEVEMENT, 20.0);
        valueProfile.setScore(ValueSchwartz.POWER_DOMINANCE, 25.0);
        valueProfile.setScore(ValueSchwartz.HEDONISM, 40.0);
        Map<ValueSchwartz, Double> suppressed = valueProfile.getSuppressedValues();
        assertEquals(2, suppressed.size());
        assertTrue(suppressed.containsKey(ValueSchwartz.ACHIEVEMENT));
        assertTrue(suppressed.containsKey(ValueSchwartz.POWER_DOMINANCE));
    }

    @Test
    void testGetStrongValues() {
        valueProfile.setScore(ValueSchwartz.ACHIEVEMENT, 80.0);
        valueProfile.setScore(ValueSchwartz.POWER_DOMINANCE, 75.0);
        valueProfile.setScore(ValueSchwartz.HEDONISM, 60.0);
        Map<ValueSchwartz, Double> strong = valueProfile.getStrongValues();
        assertEquals(2, strong.size());
        assertTrue(strong.containsKey(ValueSchwartz.ACHIEVEMENT));
        assertTrue(strong.containsKey(ValueSchwartz.POWER_DOMINANCE));
    }
}
