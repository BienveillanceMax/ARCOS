package org.arcos.UserModel.PersonaTree;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * PersonaTreeGate is the single public entry point for the UserModel.PersonaTree module.
 * It delegates all operations to the appropriate internal services without adding business logic.
 *
 * This facade provides:
 * - Reading operations (leaf values, counts, schema queries)
 * - Writing operations (apply operations, increment counter)
 * - Persistence operations (persist, snapshot)
 */
@Slf4j
@Service
public class PersonaTreeGate {

    private final PersonaTreeService treeService;
    private final TreeOperationService operationService;
    private final PersonaTreeSchemaLoader schemaLoader;

    public PersonaTreeGate(PersonaTreeService treeService,
                           TreeOperationService operationService,
                           PersonaTreeSchemaLoader schemaLoader) {
        this.treeService = treeService;
        this.operationService = operationService;
        this.schemaLoader = schemaLoader;
    }

    // ========== Reading ==========

    /**
     * Get the value of a leaf node.
     * @param dotPath dot-separated path (e.g., "1_Biological_Characteristics.Physical_Appearance.Hair.Scalp_Hair")
     * @return Optional containing the value, or empty if the path is invalid or the value is null/empty
     */
    public Optional<String> getLeafValue(String dotPath) {
        String value = treeService.getLeafValue(dotPath);
        if (value == null || value.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    /**
     * Get all non-empty leaves as a map of path → value.
     * @return map with all filled leaves
     */
    public Map<String, String> getNonEmptyLeaves() {
        return treeService.getNonEmptyLeaves();
    }

    /**
     * Get all non-empty leaves under a given path prefix.
     * @param pathPrefix dot-separated path prefix
     * @return map of matching path → value
     */
    public Map<String, String> getLeavesUnderPath(String pathPrefix) {
        return treeService.getLeavesUnderPath(pathPrefix);
    }

    /**
     * Count the number of non-empty leaves.
     * @return count of filled leaves
     */
    public int getFilledLeafCount() {
        return treeService.getNonEmptyLeafCount();
    }

    /**
     * Get the conversation count.
     * @return current conversation count
     */
    public int getConversationCount() {
        return treeService.getConversationCount();
    }

    // ========== Writing ==========

    /**
     * Parse and apply LLM-generated operations from raw text output.
     * @param rawOutput multi-line text with operation syntax
     * @return list of results (success/failure per operation)
     */
    public List<TreeOperationResult> applyRawOperations(String rawOutput) {
        return operationService.parseAndApply(rawOutput);
    }

    /**
     * Apply a batch of TreeOperations to the tree.
     * @param operations list of operations to apply
     * @return list of results (success/failure per operation)
     */
    public List<TreeOperationResult> applyOperations(List<TreeOperation> operations) {
        return operationService.applyOperations(operations);
    }

    /**
     * Increment the conversation count.
     */
    public void incrementConversationCount() {
        treeService.incrementConversationCount();
    }

    // ========== Persistence ==========

    /**
     * Persist the current tree to disk.
     */
    public void persist() {
        treeService.persist();
    }

    /**
     * Create a timestamped snapshot of the tree.
     * @return path to the snapshot file
     */
    public Path createSnapshot() {
        return treeService.createSnapshot();
    }

    // ========== Schema ==========

    /**
     * Get all valid leaf paths defined in the schema.
     * @return set of valid dot-separated leaf paths
     */
    public Set<String> getValidLeafPaths() {
        return schemaLoader.getValidLeafPaths();
    }

    /**
     * Check if a given path is a valid leaf path according to the schema.
     * @param dotPath dot-separated path to check
     * @return true if the path is valid and points to a leaf
     */
    public boolean isValidLeafPath(String dotPath) {
        return schemaLoader.isValidLeafPath(dotPath);
    }
}
