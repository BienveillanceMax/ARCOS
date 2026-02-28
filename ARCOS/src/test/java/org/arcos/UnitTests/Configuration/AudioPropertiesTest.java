package org.arcos.UnitTests.Configuration;

import org.arcos.Configuration.AudioProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AudioPropertiesTest {

    @Test
    void defaultValues_areCorrect() {
        // Given
        AudioProperties props = new AudioProperties();

        // Then
        assertEquals(-1, props.getInputDeviceIndex());
        assertEquals(44100, props.getSampleRate());
        assertEquals(1000, props.getSilenceThreshold());
        assertEquals(1200, props.getSilenceDurationMs());
        assertEquals(30, props.getMaxRecordingSeconds());
    }

    @Test
    void setters_updateValues() {
        // Given
        AudioProperties props = new AudioProperties();

        // When
        props.setInputDeviceIndex(5);
        props.setSampleRate(16000);
        props.setSilenceThreshold(500);
        props.setSilenceDurationMs(800);
        props.setMaxRecordingSeconds(60);

        // Then
        assertEquals(5, props.getInputDeviceIndex());
        assertEquals(16000, props.getSampleRate());
        assertEquals(500, props.getSilenceThreshold());
        assertEquals(800, props.getSilenceDurationMs());
        assertEquals(60, props.getMaxRecordingSeconds());
    }
}
