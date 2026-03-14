package org.arcos.UserModel.PersonaTree;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class TreeOperationService {

    private final PersonaTreeService treeService;
    private final PersonaTreeSchemaLoader schemaLoader;

    // Case-insensitive regex patterns
    private static final Pattern ADD_PATTERN = Pattern.compile(
            "ADD\\(\\s*([^,]+?)\\s*,\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern UPDATE_PATTERN = Pattern.compile(
            "UPDATE\\(\\s*([^,]+?)\\s*,\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DELETE_PATTERN = Pattern.compile(
            "DELETE\\(\\s*([^,]+?)\\s*,\\s*(?:None|null)\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NO_OP_PATTERN = Pattern.compile(
            "NO_OP\\(\\s*\\)", Pattern.CASE_INSENSITIVE);

    public TreeOperationService(PersonaTreeService treeService, PersonaTreeSchemaLoader schemaLoader) {
        this.treeService = treeService;
        this.schemaLoader = schemaLoader;
    }

    /**
     * Parse multi-line LLM output into TreeOperation records.
     * Skips unparseable lines (logs at WARN).
     * Case-insensitive operation names.
     *
     * @param rawOutput multi-line text with operation syntax
     * @return list of parsed operations
     */
    public List<TreeOperation> parseOperations(String rawOutput) {
        List<TreeOperation> operations = new ArrayList<>();
        if (rawOutput == null || rawOutput.isEmpty()) {
            return operations;
        }

        String[] lines = rawOutput.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            TreeOperation op = parseSingleLine(trimmed);
            if (op != null) {
                operations.add(op);
            } else {
                log.warn("Skipping unparseable line: {}", trimmed);
            }
        }

        return operations;
    }

    /**
     * Apply a batch of TreeOperations to the tree.
     * Validates paths and calls appropriate tree service methods.
     * Persists the tree after all operations are applied.
     *
     * @param operations list of operations to apply
     * @return list of results (success/failure per operation)
     */
    public List<TreeOperationResult> applyOperations(List<TreeOperation> operations) {
        List<TreeOperationResult> results = new ArrayList<>();

        for (TreeOperation op : operations) {
            TreeOperationResult result = applyOperation(op);
            results.add(result);
        }

        // Persist after batch
        treeService.persist();

        return results;
    }

    /**
     * Convenience method: parse and apply in one call.
     * Persists the tree after all operations are applied.
     *
     * @param rawOutput multi-line LLM output
     * @return list of results
     */
    public List<TreeOperationResult> parseAndApply(String rawOutput) {
        List<TreeOperation> operations = parseOperations(rawOutput);
        return applyOperations(operations);
    }

    // ========== Internal Helpers ==========

    /**
     * Parse a single line into a TreeOperation.
     *
     * @param line trimmed line
     * @return TreeOperation or null if unparseable
     */
    private TreeOperation parseSingleLine(String line) {
        // Try NO_OP first (no parameters)
        Matcher noOpMatcher = NO_OP_PATTERN.matcher(line);
        if (noOpMatcher.find()) {
            return new TreeOperation(TreeOperationType.NO_OP, null, null);
        }

        // Try ADD
        Matcher addMatcher = ADD_PATTERN.matcher(line);
        if (addMatcher.find()) {
            String path = addMatcher.group(1).trim();
            String value = addMatcher.group(2);
            return new TreeOperation(TreeOperationType.ADD, path, value);
        }

        // Try UPDATE
        Matcher updateMatcher = UPDATE_PATTERN.matcher(line);
        if (updateMatcher.find()) {
            String path = updateMatcher.group(1).trim();
            String value = updateMatcher.group(2);
            return new TreeOperation(TreeOperationType.UPDATE, path, value);
        }

        // Try DELETE
        Matcher deleteMatcher = DELETE_PATTERN.matcher(line);
        if (deleteMatcher.find()) {
            String path = deleteMatcher.group(1).trim();
            return new TreeOperation(TreeOperationType.DELETE, path, null);
        }

        return null;
    }

    /**
     * Apply a single TreeOperation.
     *
     * @param operation the operation to apply
     * @return result with success status and optional error message
     */
    private TreeOperationResult applyOperation(TreeOperation operation) {
        try {
            switch (operation.type()) {
                case ADD, UPDATE -> {
                    // Both ADD and UPDATE set a leaf value (semantically different, functionally identical)
                    if (!schemaLoader.isValidLeafPath(operation.path())) {
                        return new TreeOperationResult(operation, false,
                                "Invalid or non-leaf path: " + operation.path());
                    }
                    treeService.setLeafValue(operation.path(), operation.value());
                    return new TreeOperationResult(operation, true, null);
                }
                case DELETE -> {
                    if (!schemaLoader.isValidLeafPath(operation.path())) {
                        return new TreeOperationResult(operation, false,
                                "Invalid or non-leaf path: " + operation.path());
                    }
                    treeService.clearLeafValue(operation.path());
                    return new TreeOperationResult(operation, true, null);
                }
                case NO_OP -> {
                    // NO_OP always succeeds with no action
                    return new TreeOperationResult(operation, true, null);
                }
                default -> {
                    return new TreeOperationResult(operation, false,
                            "Unknown operation type: " + operation.type());
                }
            }
        } catch (Exception e) {
            log.error("Error applying operation {}: {}", operation, e.getMessage(), e);
            return new TreeOperationResult(operation, false, e.getMessage());
        }
    }
}
