package org.arcos.UserModel.BatchPipeline.Queue;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.arcos.UserModel.UserModelProperties;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ConversationQueueService {

    private final ConversationQueueRepository repository;
    private final Path queueFilePath;
    private final List<QueuedConversation> queue = new ArrayList<>();

    public ConversationQueueService(ConversationQueueRepository repository,
                                    UserModelProperties properties) {
        this.repository = repository;
        this.queueFilePath = Paths.get(properties.getConversationQueuePath());
    }

    @PostConstruct
    public void initialize() {
        synchronized (queue) {
            List<QueuedConversation> loaded = repository.load(queueFilePath);
            queue.addAll(loaded);
            log.info("Loaded {} queued conversations from disk", queue.size());
        }
    }

    public void enqueue(QueuedConversation conversation) {
        synchronized (queue) {
            queue.add(conversation);
            repository.save(new ArrayList<>(queue), queueFilePath);
            log.debug("Enqueued conversation {}, queue size: {}", conversation.id(), queue.size());
        }
    }

    public List<QueuedConversation> drainAll() {
        synchronized (queue) {
            List<QueuedConversation> drained = new ArrayList<>(queue);
            queue.clear();
            repository.save(new ArrayList<>(queue), queueFilePath);
            log.info("Drained {} conversations from queue", drained.size());
            return drained;
        }
    }

    public boolean isEmpty() {
        synchronized (queue) {
            return queue.isEmpty();
        }
    }

    public int size() {
        synchronized (queue) {
            return queue.size();
        }
    }
}
