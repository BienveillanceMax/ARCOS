package org.arcos.UnitTests.Configuration;

import org.arcos.Configuration.PersonalityProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PersonalityPropertiesTest {

    @Test
    void defaultValues_areCorrect() {
        // Given
        PersonalityProperties props = new PersonalityProperties();

        // Then
        assertEquals("DEFAULT", props.getProfile());
        assertEquals(0.5, props.getOpinionThreshold(), 0.001);
        assertEquals(0.8, props.getDesireHighThreshold(), 0.001);
        assertEquals(0.3, props.getDesireLowThreshold(), 0.001);
    }

    @Test
    void setProfile_updatesValue() {
        // Given
        PersonalityProperties props = new PersonalityProperties();

        // When
        props.setProfile("CALCIFER");

        // Then
        assertEquals("CALCIFER", props.getProfile());
    }

    @Test
    void setThresholds_updateValues() {
        // Given
        PersonalityProperties props = new PersonalityProperties();

        // When
        props.setOpinionThreshold(0.7);
        props.setDesireHighThreshold(0.9);
        props.setDesireLowThreshold(0.2);

        // Then
        assertEquals(0.7, props.getOpinionThreshold(), 0.001);
        assertEquals(0.9, props.getDesireHighThreshold(), 0.001);
        assertEquals(0.2, props.getDesireLowThreshold(), 0.001);
    }
}
