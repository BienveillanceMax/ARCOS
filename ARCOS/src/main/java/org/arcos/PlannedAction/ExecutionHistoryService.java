package org.arcos.PlannedAction;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.arcos.Configuration.PlannedActionProperties;
import org.arcos.PlannedAction.Models.ExecutionHistoryEntry;
import org.arcos.PlannedAction.Models.PlannedActionEntry;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ExecutionHistoryService {

    private final Path storageFile;
    private final ObjectMapper objectMapper;
    private final List<ExecutionHistoryEntry> history;

    public ExecutionHistoryService(PlannedActionProperties properties) {
        this.storageFile = Paths.get(properties.getHistoryStoragePath());
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.history = Collections.synchronizedList(new java.util.ArrayList<>());
    }

    @PostConstruct
    public void init() {
        loadFromFile();
    }

    public void recordExecution(PlannedActionEntry action, String result, boolean success) {
        ExecutionHistoryEntry entry = new ExecutionHistoryEntry(
                action.getId(),
                action.getLabel(),
                result,
                success,
                action.hasContext() ? action.getContext() : null
        );
        history.add(entry);
        persistToFile();
        log.info("Recorded execution for '{}': success={}", action.getLabel(), success);
    }

    public List<ExecutionHistoryEntry> getHistoryForAction(String actionId, int limit) {
        return history.stream()
                .filter(e -> e.getActionId().equals(actionId))
                .sorted(Comparator.comparing(ExecutionHistoryEntry::getExecutedAt).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<ExecutionHistoryEntry> searchHistoryByLabel(String label, int limit) {
        String lowerLabel = label.toLowerCase();
        return history.stream()
                .filter(e -> e.getLabel().toLowerCase().contains(lowerLabel))
                .sorted(Comparator.comparing(ExecutionHistoryEntry::getExecutedAt).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    private void persistToFile() {
        try {
            Files.createDirectories(storageFile.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storageFile.toFile(), history);
            log.debug("Persisted {} execution history entries to {}", history.size(), storageFile);
        } catch (IOException e) {
            log.error("Failed to persist execution history to {}", storageFile, e);
        }
    }

    private void loadFromFile() {
        if (!Files.exists(storageFile)) {
            log.info("No execution history file found at {}, starting fresh", storageFile);
            return;
        }

        try {
            List<ExecutionHistoryEntry> loaded = objectMapper.readValue(
                    storageFile.toFile(),
                    new TypeReference<List<ExecutionHistoryEntry>>() {}
            );
            history.addAll(loaded);
            log.info("Loaded {} execution history entries from file", history.size());
        } catch (IOException e) {
            log.error("Failed to load execution history from {}, starting with empty list", storageFile, e);
        }
    }
}
