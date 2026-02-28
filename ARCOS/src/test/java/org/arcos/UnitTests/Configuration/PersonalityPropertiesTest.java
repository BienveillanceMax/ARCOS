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
    void defaultOpinionSearchValues_areCorrect() {
        PersonalityProperties props = new PersonalityProperties();

        assertEquals(0.85, props.getOpinionSimilarityThreshold(), 0.001);
        assertEquals(10, props.getOpinionSearchTopk());
    }

    @Test
    void defaultDesireThresholds_areCorrect() {
        PersonalityProperties props = new PersonalityProperties();

        assertEquals(0.5, props.getDesireCreateThreshold(), 0.001);
        assertEquals(0.7, props.getDesirePendingThreshold(), 0.001);
        assertEquals(0.7, props.getDesireSmoothingFactor(), 0.001);
    }

    @Test
    void defaultInitiativeConfig_isCorrect() {
        PersonalityProperties props = new PersonalityProperties();

        assertEquals(0.8, props.getInitiativeThreshold(), 0.001);
        assertEquals(9, props.getInitiativeNoInitiativeUntilHour());
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
        props.setDesireCreateThreshold(0.6);
        props.setDesirePendingThreshold(0.8);
        props.setDesireSmoothingFactor(0.5);
        props.setInitiativeThreshold(0.9);
        props.setInitiativeNoInitiativeUntilHour(7);
        props.setOpinionSimilarityThreshold(0.9);
        props.setOpinionSearchTopk(15);

        // Then
        assertEquals(0.7, props.getOpinionThreshold(), 0.001);
        assertEquals(0.9, props.getDesireHighThreshold(), 0.001);
        assertEquals(0.2, props.getDesireLowThreshold(), 0.001);
        assertEquals(0.6, props.getDesireCreateThreshold(), 0.001);
        assertEquals(0.8, props.getDesirePendingThreshold(), 0.001);
        assertEquals(0.5, props.getDesireSmoothingFactor(), 0.001);
        assertEquals(0.9, props.getInitiativeThreshold(), 0.001);
        assertEquals(7, props.getInitiativeNoInitiativeUntilHour());
        assertEquals(0.9, props.getOpinionSimilarityThreshold(), 0.001);
        assertEquals(15, props.getOpinionSearchTopk());
    }
}
