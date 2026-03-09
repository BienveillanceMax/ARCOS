package org.arcos.Setup.Persistence;

import org.arcos.Setup.ConfigurationModel;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Lightweight parser for application-local.yaml.
 * Reads only the properties written by ConfigurationWriter:
 *   arcos.personality.profile and arcos.audio.input-device-index.
 * No YAML library dependency — simple line-based parsing.
 */
public final class LocalYamlParser {

    private LocalYamlParser() {}

    /**
     * Reads application-local.yaml and sets personality profile / audio device
     * on the given model (only if present in the file and not already set
     * to a non-default value).
     */
    public static void enrichModel(File yamlFile, ConfigurationModel model) throws IOException {
        if (!yamlFile.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(yamlFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("#") || trimmed.isEmpty()) continue;

                if (trimmed.startsWith("profile:")) {
                    String value = extractValue(trimmed);
                    if (value != null && !value.isBlank()) {
                        model.setPersonalityProfile(value);
                    }
                } else if (trimmed.startsWith("input-device-index:")) {
                    String value = extractValue(trimmed);
                    if (value != null && !value.isBlank()) {
                        try {
                            model.setAudioDeviceIndex(Integer.parseInt(value));
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }
    }

    private static String extractValue(String line) {
        int colonIdx = line.indexOf(':');
        if (colonIdx < 0 || colonIdx == line.length() - 1) return null;
        return line.substring(colonIdx + 1).trim();
    }
}
