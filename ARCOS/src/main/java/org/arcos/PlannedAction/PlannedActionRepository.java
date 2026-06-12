package org.arcos.PlannedAction;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.arcos.Configuration.PlannedActionProperties;
import org.arcos.PlannedAction.Models.ActionStatus;
import org.arcos.PlannedAction.Models.PlannedActionEntry;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class PlannedActionRepository {

    private final Map<String, PlannedActionEntry> actions = new ConcurrentHashMap<>();
    private final Path storageFile;
    private final ObjectMapper objectMapper;

    public PlannedActionRepository(PlannedActionProperties properties) {
        this.storageFile = Paths.get(properties.getStoragePath());
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @PostConstruct
    public void init() {
        loadFromFile();
    }

    public void save(PlannedActionEntry entry) {
        actions.put(entry.getId(), entry);
        persistToFile();
    }

    public PlannedActionEntry delete(String id) {
        PlannedActionEntry removed = actions.remove(id);
        if (removed != null) {
            persistToFile();
        }
        return removed;
    }

    public PlannedActionEntry findById(String id) {
        return actions.get(id);
    }

    public PlannedActionEntry findActiveByLabelContaining(String label) {
        return actions.values().stream()
                .filter(a -> a.getStatus() == ActionStatus.ACTIVE)
                .filter(a -> a.getLabel().toLowerCase().contains(label.toLowerCase()))
                .findFirst()
                .orElse(null);
    }

    public List<PlannedActionEntry> findAllActive() {
        return actions.values().stream()
                .filter(a -> a.getStatus() == ActionStatus.ACTIVE)
                .collect(Collectors.toList());
    }

    public List<PlannedActionEntry> findAll() {
        return List.copyOf(actions.values());
    }

    private void persistToFile() {
        try {
            Files.createDirectories(storageFile.getParent());
            Path tmp = Files.createTempFile(storageFile.getParent(), "planned-actions-", ".tmp");
            try {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), actions);
                try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.WRITE)) {
                    ch.force(true); // fsync AVANT le rename : sans ça, une coupure de courant
                                    // peut laisser un fichier vide malgré le move atomique
                }
                try {
                    Files.move(tmp, storageFile,
                            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(tmp, storageFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
            log.debug("Persisted {} planned actions to {}", actions.size(), storageFile);
        } catch (IOException e) {
            log.error("Failed to persist planned actions to {}", storageFile, e);
        }
    }

    private void loadFromFile() {
        if (!Files.exists(storageFile)) {
            log.info("No planned actions file found at {}, starting fresh", storageFile);
            return;
        }

        try {
            Map<String, PlannedActionEntry> loaded = objectMapper.readValue(
                    storageFile.toFile(),
                    new TypeReference<Map<String, PlannedActionEntry>>() {}
            );
            actions.putAll(loaded);
            log.info("Loaded {} planned actions from file", actions.size());
        } catch (IOException e) {
            log.error("Failed to load planned actions from {}, starting with empty map", storageFile, e);
        }
    }
}
