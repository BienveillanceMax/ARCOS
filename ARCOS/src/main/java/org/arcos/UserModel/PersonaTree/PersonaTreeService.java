package org.arcos.UserModel.PersonaTree;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.arcos.UserModel.UserModelProperties;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
@Service
public class PersonaTreeService {

    private final PersonaTreeSchemaLoader schemaLoader;
    private final PersonaTreeRepository repository;
    private final UserModelProperties properties;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final ScheduledExecutorService scheduler;
    private volatile ScheduledFuture<?> pendingSave;

    private PersonaTree tree;

    public PersonaTreeService(PersonaTreeSchemaLoader schemaLoader,
                              PersonaTreeRepository repository,
                              UserModelProperties properties) {
        this.schemaLoader = schemaLoader;
        this.repository = repository;
        this.properties = properties;
        ThreadFactory daemonFactory = r -> {
            Thread t = new Thread(r, "persona-tree-persistence");
            t.setDaemon(true);
            return t;
        };
        this.scheduler = Executors.newSingleThreadScheduledExecutor(daemonFactory);
    }

    /**
     * Initialize the tree from disk or schema.
     */
    @PostConstruct
    public void initialize() {
        Path treePath = Paths.get(properties.getPersonaTreePath());
        Optional<PersonaTree> loaded = repository.load(treePath);

        if (loaded.isPresent()) {
            tree = loaded.get();
            log.info("Loaded PersonaTree from {}: {} conversations, {} filled leaves",
                    treePath, tree.getConversationCount(), getNonEmptyLeafCount());
        } else {
            tree = schemaLoader.loadSchema();
            log.info("Created empty PersonaTree from schema: {} root categories",
                    tree.getRoots().size());
        }
    }

    // ========== Reading (ReadLock) ==========

    /**
     * Get the value of a leaf node.
     * @param dotPath dot-separated path (e.g., "1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair")
     * @return the leaf value, empty string if unset, or null if path is invalid
     */
    public String getLeafValue(String dotPath) {
        lock.readLock().lock();
        try {
            PersonaNode node = navigateToNode(dotPath);
            if (node == null) {
                return null; // invalid path
            }
            if (!node.isLeaf()) {
                return null; // path points to a branch, not a leaf
            }
            return node.getValue();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get all non-empty leaves as a map of path → value.
     * @return map with all filled leaves
     */
    public Map<String, String> getNonEmptyLeaves() {
        lock.readLock().lock();
        try {
            Map<String, String> result = new LinkedHashMap<>();
            for (var entry : tree.getRoots().entrySet()) {
                collectNonEmptyLeaves(entry.getKey(), entry.getValue(), result);
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get all non-empty leaves under a given path prefix.
     * @param pathPrefix dot-separated path prefix
     * @return map of matching path → value
     */
    public Map<String, String> getLeavesUnderPath(String pathPrefix) {
        lock.readLock().lock();
        try {
            Map<String, String> allLeaves = new LinkedHashMap<>();
            for (var entry : tree.getRoots().entrySet()) {
                collectNonEmptyLeaves(entry.getKey(), entry.getValue(), allLeaves);
            }

            Map<String, String> filtered = new LinkedHashMap<>();
            String normalizedPrefix = pathPrefix.endsWith(".") ? pathPrefix : pathPrefix + ".";
            for (var entry : allLeaves.entrySet()) {
                if (entry.getKey().startsWith(normalizedPrefix) || entry.getKey().equals(pathPrefix)) {
                    filtered.put(entry.getKey(), entry.getValue());
                }
            }
            return filtered;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Count the number of non-empty leaves.
     * @return count of filled leaves
     */
    public int getNonEmptyLeafCount() {
        lock.readLock().lock();
        try {
            return getNonEmptyLeaves().size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get the conversation count.
     * @return current conversation count
     */
    public int getConversationCount() {
        lock.readLock().lock();
        try {
            return tree.getConversationCount();
        } finally {
            lock.readLock().unlock();
        }
    }

    // ========== Writing (WriteLock) ==========

    /**
     * Set the value of a leaf node.
     * @param dotPath dot-separated path
     * @param value new value for the leaf
     * @throws IllegalArgumentException if path is invalid or not a leaf
     */
    public void setLeafValue(String dotPath, String value) {
        if (!schemaLoader.isValidLeafPath(dotPath)) {
            throw new IllegalArgumentException("Invalid or non-leaf path: " + dotPath);
        }

        lock.writeLock().lock();
        try {
            PersonaNode node = navigateToNode(dotPath);
            if (node == null || !node.isLeaf()) {
                throw new IllegalArgumentException("Path does not point to a valid leaf: " + dotPath);
            }
            node.setValue(value != null ? value : "");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Clear the value of a leaf node (set to empty string).
     * @param dotPath dot-separated path
     * @throws IllegalArgumentException if path is invalid
     */
    public void clearLeafValue(String dotPath) {
        setLeafValue(dotPath, "");
    }

    /**
     * Increment the conversation count.
     */
    public void incrementConversationCount() {
        lock.writeLock().lock();
        try {
            tree.incrementConversationCount();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Replace the entire tree (used by migration).
     * @param newTree replacement tree
     */
    public void replaceTree(PersonaTree newTree) {
        lock.writeLock().lock();
        try {
            this.tree = newTree;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ========== Persistence ==========

    /**
     * Schedule a persist with debounce.
     * Cancels any pending persist and schedules a new one.
     */
    public void schedulePersist() {
        ScheduledFuture<?> existing = pendingSave;
        if (existing != null) {
            existing.cancel(false);
        }
        pendingSave = scheduler.schedule(this::persist,
                properties.getDebounceSaveMs(), TimeUnit.MILLISECONDS);
    }

    /**
     * Persist the current tree to disk.
     * Creates a deep copy under ReadLock, then saves outside the lock.
     */
    public void persist() {
        PersonaTree snapshot;
        lock.readLock().lock();
        try {
            snapshot = tree.deepCopy();
        } finally {
            lock.readLock().unlock();
        }

        Path treePath = Paths.get(properties.getPersonaTreePath());
        repository.save(snapshot, treePath);
        log.debug("Persisted PersonaTree to {}", treePath);
    }

    /**
     * Create a timestamped snapshot of the tree.
     * @return path to the snapshot file
     */
    public Path createSnapshot() {
        PersonaTree snapshot;
        lock.readLock().lock();
        try {
            snapshot = tree.deepCopy();
        } finally {
            lock.readLock().unlock();
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String snapshotFileName = "persona-tree-snapshot-" + timestamp + ".json";
        Path snapshotPath = Paths.get(properties.getPersonaTreePath()).getParent().resolve(snapshotFileName);

        repository.save(snapshot, snapshotPath);
        log.info("Created PersonaTree snapshot at {}", snapshotPath);
        return snapshotPath;
    }

    /**
     * Flush any pending save and shut down the scheduler on context close.
     */
    @PreDestroy
    public void shutdown() {
        ScheduledFuture<?> pending = pendingSave;
        if (pending != null) {
            pending.cancel(false);
        }
        persist();
        scheduler.shutdown();
    }

    // ========== Internal Helpers ==========

    /**
     * Navigate to a node by dot-separated path.
     * Must be called under lock (read or write).
     * @param dotPath the path to navigate
     * @return the node, or null if not found
     */
    private PersonaNode navigateToNode(String dotPath) {
        if (dotPath == null || dotPath.isEmpty()) {
            return null;
        }

        String[] segments = dotPath.split("\\.");
        if (segments.length == 0) {
            return null;
        }

        PersonaNode current = tree.getRoots().get(segments[0]);
        if (current == null) {
            return null;
        }

        for (int i = 1; i < segments.length; i++) {
            if (current.isLeaf()) {
                return null; // hit a leaf before reaching target
            }
            current = current.getChildren().get(segments[i]);
            if (current == null) {
                return null;
            }
        }

        return current;
    }

    /**
     * Recursively collect non-empty leaves.
     * Must be called under read lock.
     * @param currentPath current path being explored
     * @param node current node
     * @param result accumulator map
     */
    private void collectNonEmptyLeaves(String currentPath, PersonaNode node, Map<String, String> result) {
        if (node.isLeaf()) {
            String value = node.getValue();
            if (value != null && !value.isEmpty()) {
                result.put(currentPath, value);
            }
        } else if (node.getChildren() != null) {
            for (var entry : node.getChildren().entrySet()) {
                collectNonEmptyLeaves(currentPath + "." + entry.getKey(), entry.getValue(), result);
            }
        }
    }
}
