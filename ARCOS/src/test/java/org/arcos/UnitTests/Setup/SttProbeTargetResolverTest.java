package org.arcos.UnitTests.Setup;

import org.arcos.IO.InputHandling.STT.SttBackendType;
import org.arcos.Setup.Health.SttProbeTargetResolver;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SttProbeTargetResolverTest {

    @Test
    void resolve_backendWhisperCppFromProperties_targetsWhisperCppUrl() {
        // Given
        Properties props = new Properties();
        props.setProperty("arcos.stt.backend", "WHISPER_CPP");
        props.setProperty("arcos.stt.whisper-cpp-url", "http://localhost:8090");
        props.setProperty("arcos.stt.faster-whisper-url", "http://localhost:8000");

        // When
        SttProbeTargetResolver.SttTarget target = SttProbeTargetResolver.resolve(Map.of(), props);

        // Then
        assertEquals(SttBackendType.WHISPER_CPP, target.backend());
        assertEquals("localhost", target.host());
        assertEquals(8090, target.port());
    }

    @Test
    void resolve_backendFasterWhisperFromProperties_targetsFasterWhisperUrl() {
        // Given
        Properties props = new Properties();
        props.setProperty("arcos.stt.backend", "FASTER_WHISPER");
        props.setProperty("arcos.stt.faster-whisper-url", "http://stt-host:9000");

        // When
        SttProbeTargetResolver.SttTarget target = SttProbeTargetResolver.resolve(Map.of(), props);

        // Then
        assertEquals(SttBackendType.FASTER_WHISPER, target.backend());
        assertEquals("stt-host", target.host());
        assertEquals(9000, target.port());
    }

    @Test
    void resolve_envOverridesProperties() {
        // Given
        Properties props = new Properties();
        props.setProperty("arcos.stt.backend", "FASTER_WHISPER");
        props.setProperty("arcos.stt.whisper-cpp-url", "http://localhost:8090");
        Map<String, String> env = Map.of(
                "ARCOS_STT_BACKEND", "WHISPER_CPP",
                "ARCOS_STT_WHISPER_CPP_URL", "http://192.168.1.50:8091");

        // When
        SttProbeTargetResolver.SttTarget target = SttProbeTargetResolver.resolve(env, props);

        // Then
        assertEquals(SttBackendType.WHISPER_CPP, target.backend());
        assertEquals("192.168.1.50", target.host());
        assertEquals(8091, target.port());
    }

    @Test
    void resolve_nothingConfigured_defaultsToFasterWhisperPort8000() {
        // When
        SttProbeTargetResolver.SttTarget target = SttProbeTargetResolver.resolve(Map.of(), new Properties());

        // Then
        assertEquals(SttBackendType.FASTER_WHISPER, target.backend());
        assertEquals("localhost", target.host());
        assertEquals(8000, target.port());
    }

    @Test
    void resolve_whisperCppWithoutExplicitUrl_defaultsToPort8090() {
        // Given
        Properties props = new Properties();
        props.setProperty("arcos.stt.backend", "WHISPER_CPP");

        // When
        SttProbeTargetResolver.SttTarget target = SttProbeTargetResolver.resolve(Map.of(), props);

        // Then
        assertEquals("localhost", target.host());
        assertEquals(8090, target.port());
    }

    @Test
    void resolve_urlWithoutPort_fallsBackToBackendDefaultPort() {
        // Given
        Properties props = new Properties();
        props.setProperty("arcos.stt.backend", "WHISPER_CPP");
        props.setProperty("arcos.stt.whisper-cpp-url", "http://stt-box");

        // When
        SttProbeTargetResolver.SttTarget target = SttProbeTargetResolver.resolve(Map.of(), props);

        // Then
        assertEquals("stt-box", target.host());
        assertEquals(8090, target.port());
    }

    @Test
    void resolve_invalidBackendValue_fallsBackToFasterWhisper() {
        // Given
        Properties props = new Properties();
        props.setProperty("arcos.stt.backend", "GIBBERISH");

        // When
        SttProbeTargetResolver.SttTarget target = SttProbeTargetResolver.resolve(Map.of(), props);

        // Then
        assertEquals(SttBackendType.FASTER_WHISPER, target.backend());
        assertEquals(8000, target.port());
    }
}
