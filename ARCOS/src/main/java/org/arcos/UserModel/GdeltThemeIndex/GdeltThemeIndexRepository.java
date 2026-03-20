package org.arcos.UserModel.GdeltThemeIndex;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ConditionalOnProperty(name = "arcos.gdelt.enabled", havingValue = "true", matchIfMissing = true)
public class GdeltThemeIndexRepository {

    private static final int CURRENT_VERSION = 1;

    private final ObjectMapper objectMapper;

    public GdeltThemeIndexRepository() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public ConcurrentHashMap<String, GdeltLeafThemes> load(Path filePath) {
        if (!Files.exists(filePath)) {
            log.debug("GDELT theme index file not found at {}, starting empty", filePath);
            return new ConcurrentHashMap<>();
        }

        try {
            byte[] content = Files.readAllBytes(filePath);
            Map<String, Object> wrapper = objectMapper.readValue(content,
                    new TypeReference<Map<String, Object>>() {});

            Object entriesData = wrapper.get("entries");
            if (entriesData == null) {
                log.warn("GDELT theme index file {} missing 'entries' key", filePath);
                return new ConcurrentHashMap<>();
            }

            byte[] entriesJson = objectMapper.writeValueAsBytes(entriesData);
            Map<String, GdeltLeafThemes> entries = objectMapper.readValue(entriesJson,
                    new TypeReference<Map<String, GdeltLeafThemes>>() {});

            log.info("Loaded GDELT theme index from {}: {} entries", filePath, entries.size());
            return new ConcurrentHashMap<>(entries);
        } catch (IOException e) {
            log.error("Failed to load GDELT theme index from {} (corrupted?): {}", filePath, e.getMessage());
            return new ConcurrentHashMap<>();
        }
    }

    public void save(Map<String, GdeltLeafThemes> entries, Path filePath) {
        Path tmpPath = Paths.get(filePath + ".tmp");
        try {
            Files.createDirectories(filePath.getParent());

            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put("version", CURRENT_VERSION);
            wrapper.put("entries", entries);

            objectMapper.writeValue(tmpPath.toFile(), wrapper);
            Files.move(tmpPath, filePath,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Saved GDELT theme index to {} ({} entries)", filePath, entries.size());
        } catch (IOException e) {
            log.error("Failed to save GDELT theme index to {}", filePath, e);
            try { Files.deleteIfExists(tmpPath); } catch (IOException ignored) {}
        }
    }
}
