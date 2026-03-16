package org.arcos.UserModel.BatchPipeline.Queue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class ConversationQueueRepository {

    private final ObjectMapper objectMapper;

    public ConversationQueueRepository() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public List<QueuedConversation> load(Path filePath) {
        if (!Files.exists(filePath)) {
            log.debug("Conversation queue file {} does not exist, returning empty list", filePath);
            return Collections.emptyList();
        }
        try {
            byte[] content = Files.readAllBytes(filePath);
            return objectMapper.readValue(content, new TypeReference<List<QueuedConversation>>() {});
        } catch (IOException e) {
            log.warn("Failed to load conversation queue from {}: {}", filePath, e.getMessage());
            return Collections.emptyList();
        }
    }

    public void save(List<QueuedConversation> queue, Path filePath) {
        Path tmpPath = Paths.get(filePath + ".tmp");
        try {
            Files.createDirectories(filePath.getParent());
            objectMapper.writeValue(tmpPath.toFile(), queue);
            Files.move(tmpPath, filePath,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Saved conversation queue ({} items) to {}", queue.size(), filePath);
        } catch (IOException e) {
            log.error("Failed to save conversation queue to {}", filePath, e);
            try {
                Files.deleteIfExists(tmpPath);
            } catch (IOException ignored) {
            }
            throw new java.io.UncheckedIOException("Failed to save conversation queue to " + filePath, e);
        }
    }
}
