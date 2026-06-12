package org.arcos.UserModel.GdeltThemeIndex;

import lombok.extern.slf4j.Slf4j;
import org.arcos.UserModel.PersonaTree.PersonaTreeService;
import org.arcos.UserModel.PersonaTree.TreeOperationType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
    private final PersonaTreeService personaTreeService;

    private ConcurrentHashMap<String, GdeltLeafThemes> index;

    private final Set<String> batchBuffer = ConcurrentHashMap.newKeySet();
    private volatile boolean batchActive = false;

    public GdeltThemeIndexService(GdeltThemeIndexRepository repository,
                                  GdeltThemeExtractor extractor,
                                  GdeltThemeIndexProperties properties,
                                  PersonaTreeService personaTreeService) {
        this.repository = repository;
        this.extractor = extractor;
        this.properties = properties;
        this.personaTreeService = personaTreeService;
        this.index = new ConcurrentHashMap<>();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcile() {
        Path indexPath = Paths.get(properties.getPath());
        this.index = repository.load(indexPath);

        Map<String, String> currentLeaves = personaTreeService.getNonEmptyLeaves();
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

    /** Open a batch: subsequent onLeafMutated calls buffer relevant paths instead of extracting/saving. */
    public void beginBatch() {
        batchBuffer.clear();
        batchActive = true;
    }

    /** Close the batch: run ONE extraction/persist pass over the buffered paths. */
    public void endBatchAndReconcile() {
        batchActive = false;
        Set<String> paths = Set.copyOf(batchBuffer);
        batchBuffer.clear();
        if (paths.isEmpty()) {
            log.debug("GDELT batch closed with no buffered paths, nothing to reconcile");
            return;
        }
        reconcilePaths(paths);
    }

    /** Targeted reconcile: hash-diff + extract + single save over a known set of paths. */
    private void reconcilePaths(Set<String> paths) {
        Path indexPath = Paths.get(properties.getPath());
        Map<String, String> currentLeaves = personaTreeService.getNonEmptyLeaves();
        int extracted = 0;
        int removed = 0;

        for (String path : paths) {
            if (!isGdeltRelevantPath(path)) continue;
            String value = currentLeaves.get(path);
            if (value == null) {
                if (index.remove(path) != null) removed++;
                continue;
            }
            String currentHash = hashValue(value);
            GdeltLeafThemes existing = index.get(path);
            if (existing != null && existing.sourceHash().equals(currentHash)) {
                continue;
            }
            List<GdeltKeyword> keywords = extractor.extract(path, value);
            if (!keywords.isEmpty()) {
                index.put(path, new GdeltLeafThemes(path, currentHash, keywords, Instant.now()));
                extracted++;
            }
        }

        if (extracted > 0 || removed > 0) {
            repository.save(index, indexPath);
        }
        log.info("GDELT batch reconcile: {} extracted, {} removed, {} buffered paths",
                extracted, removed, paths.size());
    }

    public void onLeafMutated(String path, String value, TreeOperationType type) {
        if (!isGdeltRelevantPath(path)) {
            return;
        }
        if (batchActive) {
            batchBuffer.add(path);   // defer: extraction/persist happen in endBatchAndReconcile()
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
