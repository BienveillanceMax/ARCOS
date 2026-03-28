package org.arcos.UnitTests.IO.InputHandling.STT;

import org.arcos.Configuration.SpeechToTextProperties;
import org.arcos.IO.InputHandling.STT.SttBackendType;
import org.arcos.IO.InputHandling.STT.SttGate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SttGateTest {

    @Test
    void processAudio_ShouldBufferAudioData() {
        // Given
        SttGate gate = SttGate.create(SttBackendType.FASTER_WHISPER, defaultProps());
        byte[] audioChunk = new byte[3200]; // 100ms at 16kHz 16-bit mono

        // When
        gate.processAudio(audioChunk);

        // Then
        assertThat(gate.getBufferedAudioDurationMs()).isEqualTo(100);
    }

    @Test
    void getBufferedAudioDurationMs_WhenEmpty_ShouldReturnZero() {
        // Given
        SttGate gate = SttGate.create(SttBackendType.FASTER_WHISPER, defaultProps());

        // Then
        assertThat(gate.getBufferedAudioDurationMs()).isZero();
    }

    @Test
    void hasMinimumAudio_WhenBelowThreshold_ShouldReturnFalse() {
        // Given
        SttGate gate = SttGate.create(SttBackendType.FASTER_WHISPER, defaultProps());
        gate.processAudio(new byte[320]); // 10ms — below 500ms minimum

        // Then
        assertThat(gate.hasMinimumAudio()).isFalse();
    }

    @Test
    void hasMinimumAudio_WhenAboveThreshold_ShouldReturnTrue() {
        // Given
        SttGate gate = SttGate.create(SttBackendType.FASTER_WHISPER, defaultProps());
        gate.processAudio(new byte[16000]); // 500ms at 16kHz 16-bit mono

        // Then
        assertThat(gate.hasMinimumAudio()).isTrue();
    }

    @Test
    void reset_ShouldClearBuffer() {
        // Given
        SttGate gate = SttGate.create(SttBackendType.FASTER_WHISPER, defaultProps());
        gate.processAudio(new byte[3200]);

        // When
        gate.reset();

        // Then
        assertThat(gate.getBufferedAudioDurationMs()).isZero();
    }

    @Test
    void getTranscription_WhenNoAudio_ShouldReturnEmpty() {
        // Given
        SttGate gate = SttGate.create(SttBackendType.FASTER_WHISPER, defaultProps());

        // When
        String result = gate.getTranscription();

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void close_ShouldNotThrow() {
        // Given
        SttGate gate = SttGate.create(SttBackendType.FASTER_WHISPER, defaultProps());

        // Then
        assertThatCode(gate::close).doesNotThrowAnyException();
    }

    @Test
    void create_WithFasterWhisper_ShouldSucceed() {
        // Given / When
        SttGate gate = SttGate.create(SttBackendType.FASTER_WHISPER, defaultProps());

        // Then
        assertThat(gate).isNotNull();
        gate.close();
    }

    @Test
    void create_WithWhisperCpp_ShouldSucceed() {
        // Given / When
        SttGate gate = SttGate.create(SttBackendType.WHISPER_CPP, defaultProps());

        // Then
        assertThat(gate).isNotNull();
        gate.close();
    }

    private SpeechToTextProperties defaultProps() {
        SpeechToTextProperties props = new SpeechToTextProperties();
        props.setFasterWhisperUrl("http://localhost:9999");
        props.setWhisperCppUrl("http://localhost:9998");
        return props;
    }
}
