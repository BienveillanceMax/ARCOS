package org.arcos.UserModel.BatchPipeline;

import lombok.extern.slf4j.Slf4j;
import org.arcos.UserModel.BatchPipeline.Queue.ConversationChunk;
import org.arcos.UserModel.BatchPipeline.Queue.ConversationPair;
import org.arcos.UserModel.BatchPipeline.Queue.QueuedConversation;
import org.arcos.UserModel.UserModelProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class ConversationChunker {

    private final int windowSize;
    private final int maxChars;

    public ConversationChunker(UserModelProperties properties) {
        this.windowSize = properties.getChunkWindowSize();
        this.maxChars = properties.getChunkMaxChars();
    }

    public List<ConversationChunk> chunk(QueuedConversation conversation) {
        List<ConversationPair> pairs = conversation.pairs();
        if (pairs == null || pairs.isEmpty()) {
            return List.of();
        }

        List<ConversationChunk> chunks = new ArrayList<>();
        int i = 0;

        while (i < pairs.size()) {
            int end = Math.min(i + windowSize, pairs.size());
            List<ConversationPair> windowPairs = pairs.subList(i, end);

            // Truncate to fit maxChars
            List<ConversationPair> fittingPairs = truncateToMaxChars(windowPairs);

            if (!fittingPairs.isEmpty()) {
                chunks.add(new ConversationChunk(new ArrayList<>(fittingPairs), conversation.id()));
            }

            i += windowSize;
        }

        log.debug("Chunked conversation {} ({} pairs) into {} chunks",
                conversation.id(), pairs.size(), chunks.size());
        return chunks;
    }

    private List<ConversationPair> truncateToMaxChars(List<ConversationPair> pairs) {
        List<ConversationPair> result = new ArrayList<>();
        int totalChars = 0;

        for (ConversationPair pair : pairs) {
            int pairChars = pairLength(pair);
            if (totalChars + pairChars > maxChars && !result.isEmpty()) {
                break;
            }
            result.add(pair);
            totalChars += pairChars;
        }

        return result;
    }

    private int pairLength(ConversationPair pair) {
        int length = 0;
        if (pair.userMessage() != null) {
            length += pair.userMessage().length();
        }
        if (pair.assistantMessage() != null) {
            length += pair.assistantMessage().length();
        }
        return length;
    }
}
