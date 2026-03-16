package org.arcos.UserModel.BatchPipeline.Queue;

import java.util.List;

public record ConversationChunk(
        List<ConversationPair> pairs,
        String conversationId
) {}
