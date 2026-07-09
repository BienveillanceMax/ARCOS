package org.arcos.Setup.Health;

import org.arcos.IO.InputHandling.STT.SttBackendType;

import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import java.util.Properties;

/**
 * Résout l'hôte et le port du backend STT configuré, avant le démarrage de Spring.
 * Reproduit la résolution de SpeechToTextProperties : variables d'environnement
 * (relaxed binding ARCOS_STT_*) prioritaires sur application.properties du classpath,
 * puis valeurs par défaut.
 */
public final class SttProbeTargetResolver {

    private static final String DEFAULT_FASTER_WHISPER_URL = "http://localhost:8000";
    private static final String DEFAULT_WHISPER_CPP_URL = "http://localhost:8090";

    private SttProbeTargetResolver() {}

    public record SttTarget(SttBackendType backend, String host, int port) {}

    public static SttTarget resolve() {
        return resolve(System.getenv(), loadClasspathProperties());
    }

    public static SttTarget resolve(Map<String, String> env, Properties props) {
        SttBackendType backend = parseBackend(
                firstNonBlank(env.get("ARCOS_STT_BACKEND"), props.getProperty("arcos.stt.backend")));

        String url = switch (backend) {
            case WHISPER_CPP -> firstNonBlank(
                    env.get("ARCOS_STT_WHISPER_CPP_URL"),
                    props.getProperty("arcos.stt.whisper-cpp-url"),
                    DEFAULT_WHISPER_CPP_URL);
            case FASTER_WHISPER -> firstNonBlank(
                    env.get("ARCOS_STT_FASTER_WHISPER_URL"),
                    props.getProperty("arcos.stt.faster-whisper-url"),
                    DEFAULT_FASTER_WHISPER_URL);
        };

        int defaultPort = backend == SttBackendType.WHISPER_CPP ? 8090 : 8000;
        try {
            URI u = URI.create(url);
            String host = u.getHost() != null ? u.getHost() : "localhost";
            int port = u.getPort() > 0 ? u.getPort() : defaultPort;
            return new SttTarget(backend, host, port);
        } catch (IllegalArgumentException e) {
            return new SttTarget(backend, "localhost", defaultPort);
        }
    }

    private static SttBackendType parseBackend(String value) {
        if (value == null || value.isBlank()) {
            // Défaut de SpeechToTextProperties quand ni env ni properties ne le fixent
            return SttBackendType.FASTER_WHISPER;
        }
        try {
            return SttBackendType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return SttBackendType.FASTER_WHISPER;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static Properties loadClasspathProperties() {
        Properties props = new Properties();
        try (InputStream in = SttProbeTargetResolver.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (in != null) props.load(in);
        } catch (Exception ignored) {
        }
        return props;
    }
}
