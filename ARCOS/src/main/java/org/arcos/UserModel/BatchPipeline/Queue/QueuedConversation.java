package org.arcos.UserModel.BatchPipeline.Queue;

import java.time.LocalDateTime;
import java.util.List;

public record QueuedConversation(
        String id,
        List<ConversationPair> pairs,
        LocalDateTime timestamp,
        boolean hadInitiative
) {}
