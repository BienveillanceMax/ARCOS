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
        assertEquals(500, props.getSilenceDurationMs());
        assertEquals(30, props.getMaxRecordingSeconds());
        // VAD Silero : valeurs par défaut
        assertEquals("models/silero-vad.onnx", props.getVad().getModelResource());
        assertEquals(0.5f, props.getVad().getSpeechThreshold());
    }

    @Test
    void setters_updateValues() {
        // Given
        AudioProperties props = new AudioProperties();

        // When
        props.setInputDeviceIndex(5);
        props.setSampleRate(16000);
        props.setSilenceDurationMs(800);
        props.setMaxRecordingSeconds(60);
        props.getVad().setSpeechThreshold(0.7f);

        // Then
        assertEquals(5, props.getInputDeviceIndex());
        assertEquals(16000, props.getSampleRate());
        assertEquals(800, props.getSilenceDurationMs());
        assertEquals(60, props.getMaxRecordingSeconds());
        assertEquals(0.7f, props.getVad().getSpeechThreshold());
    }
}
