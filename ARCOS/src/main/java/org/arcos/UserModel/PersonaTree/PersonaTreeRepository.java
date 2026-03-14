package org.arcos.UserModel.PersonaTree;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class PersonaTreeRepository {

    private final ObjectMapper objectMapper;

    public PersonaTreeRepository() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public Optional<PersonaTree> load(Path filePath) {
        if (!Files.exists(filePath)) {
            return Optional.empty();
        }
        try {
            byte[] content = Files.readAllBytes(filePath);
            Map<String, Object> wrapper = objectMapper.readValue(content,
                    new TypeReference<Map<String, Object>>() {});

            int conversationCount = wrapper.containsKey("conversationCount")
                    ? ((Number) wrapper.get("conversationCount")).intValue() : 0;

            Object treeData = wrapper.get("tree");
            if (treeData == null) {
                log.warn("PersonaTree file {} missing 'tree' key", filePath);
                return Optional.empty();
            }

            // Re-serialize the tree part and deserialize as PersonaNode map
            byte[] treeJson = objectMapper.writeValueAsBytes(treeData);
            LinkedHashMap<String, PersonaNode> roots = objectMapper.readValue(treeJson,
                    new TypeReference<LinkedHashMap<String, PersonaNode>>() {});

            PersonaTree tree = new PersonaTree(roots);
            tree.setConversationCount(conversationCount);
            return Optional.of(tree);
        } catch (IOException e) {
            log.warn("Failed to load PersonaTree from {} (corrupted?): {}", filePath, e.getMessage());
            return Optional.empty();
        }
    }

    public void save(PersonaTree tree, Path filePath) {
        Path tmpPath = Paths.get(filePath + ".tmp");
        try {
            Files.createDirectories(filePath.getParent());

            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put("conversationCount", tree.getConversationCount());
            wrapper.put("tree", tree.getRoots());

            objectMapper.writeValue(tmpPath.toFile(), wrapper);
            Files.move(tmpPath, filePath,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Saved PersonaTree to {}", filePath);
        } catch (IOException e) {
            log.error("Failed to save PersonaTree to {}", filePath, e);
            try { Files.deleteIfExists(tmpPath); } catch (IOException ignored) {}
            throw new java.io.UncheckedIOException("Failed to save PersonaTree to " + filePath, e);
        }
    }

    public Path createSnapshot(PersonaTree tree, Path directory) {
        String timestamp = Instant.now().toString().replace(":", "-");
        Path snapshotPath = directory.resolve("persona-tree-snapshot-" + timestamp + ".json");
        save(tree, snapshotPath);
        log.info("Created PersonaTree snapshot at {}", snapshotPath);
        return snapshotPath;
    }
}
