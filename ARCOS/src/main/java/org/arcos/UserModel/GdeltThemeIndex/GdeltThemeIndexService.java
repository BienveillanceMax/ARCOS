package org.arcos.UserModel.GdeltThemeIndex;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.arcos.UserModel.PersonaTree.PersonaTreeGate;
import org.arcos.UserModel.PersonaTree.TreeOperationType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@ConditionalOnProperty(name = "arcos.gdelt.enabled", havingValue = "true", matchIfMissing = true)
public class GdeltThemeIndexService {

    private final GdeltThemeIndexRepository repository;
    private final GdeltThemeExtractor extractor;
    private final GdeltThemeIndexProperties properties;
    private final PersonaTreeGate personaTreeGate;

    private ConcurrentHashMap<String, GdeltLeafThemes> index;

    public GdeltThemeIndexService(GdeltThemeIndexRepository repository,
                                  GdeltThemeExtractor extractor,
                                  GdeltThemeIndexProperties properties,
                                  PersonaTreeGate personaTreeGate) {
        this.repository = repository;
        this.extractor = extractor;
        this.properties = properties;
        this.personaTreeGate = personaTreeGate;
        this.index = new ConcurrentHashMap<>();
    }

    @PostConstruct
    public void reconcile() {
        Path indexPath = Paths.get(properties.getPath());
        this.index = repository.load(indexPath);

        Map<String, String> currentLeaves = personaTreeGate.getNonEmptyLeaves();
        List<String> toExtract = new ArrayList<>();
        List<String> toRemove = new ArrayList<>();

        // Find stale or missing entries for GDELT-relevant leaves
        for (Map.Entry<String, String> entry : currentLeaves.entrySet()) {
            String path = entry.getKey();
            if (!isGdeltRelevantPath(path)) continue;

            String currentHash = hashValue(entry.getValue());
            GdeltLeafThemes existing = index.get(path);

            if (existing == null || !existing.sourceHash().equals(currentHash)) {
                toExtract.add(path);
            }
        }

        // Find orphaned entries (in index but leaf is now empty or non-existent)
        for (String indexedPath : new ArrayList<>(index.keySet())) {
            if (!currentLeaves.containsKey(indexedPath)) {
                toRemove.add(indexedPath);
            }
        }

        // Apply removals
        toRemove.forEach(index::remove);

        // Apply extractions
        int extracted = 0;
        for (String path : toExtract) {
            String value = currentLeaves.get(path);
            List<GdeltKeyword> keywords = extractor.extract(path, value);
            if (!keywords.isEmpty()) {
                index.put(path, new GdeltLeafThemes(path, hashValue(value), keywords, Instant.now()));
                extracted++;
            }
            // If extraction failed (empty list), leave absent for retry on next startup
        }

        // Persist only if changes were made
        if (!toRemove.isEmpty() || extracted > 0) {
            repository.save(index, indexPath);
        }

        log.info("GDELT theme index reconciled: {} extracted, {} removed, {} orphans cleaned, {} total",
                extracted, toExtract.size() - extracted, toRemove.size(), index.size());
    }

    public void onLeafMutated(String path, String value, TreeOperationType type) {
        if (!isGdeltRelevantPath(path)) {
            return;
        }

        Path indexPath = Paths.get(properties.getPath());

        switch (type) {
            case ADD, UPDATE -> {
                List<GdeltKeyword> keywords = extractor.extract(path, value);
                if (!keywords.isEmpty()) {
                    index.put(path, new GdeltLeafThemes(path, hashValue(value), keywords, Instant.now()));
                    repository.save(index, indexPath);
                    log.debug("GDELT index updated for {} ({} keywords)", path, keywords.size());
                } else {
                    log.warn("GDELT keyword extraction returned empty for {}, not persisting", path);
                }
            }
            case DELETE -> {
                GdeltLeafThemes removed = index.remove(path);
                if (removed != null) {
                    repository.save(index, indexPath);
                    log.debug("GDELT index entry removed for {}", path);
                }
            }
            default -> { /* NO_OP — ignore */ }
        }
    }

    public ConcurrentHashMap<String, GdeltLeafThemes> getIndex() {
        return index;
    }

    public static boolean isGdeltRelevantPath(String path) {
        if (path == null) return false;
        return GdeltThemeIndexProperties.RELEVANT_LEAF_PATHS.contains(path);
    }

    public static String hashValue(String value) {
        if (value == null || value.isEmpty()) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < Math.min(8, hash.length); i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (Exception e) {
            // SHA-256 is always available, but handle gracefully
            return Integer.toHexString(value.hashCode());
        }
    }
}
