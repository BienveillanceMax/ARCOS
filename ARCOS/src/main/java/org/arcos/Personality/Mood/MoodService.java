package org.arcos.Personality.Mood;

import org.arcos.Memory.ConversationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MoodService {

    private final ConversationContext conversationContext;

    @Autowired
    public MoodService(ConversationContext conversationContext) {
        this.conversationContext = conversationContext;
    }

    public void applyMoodUpdate(MoodUpdate update) {
        if (update == null) return;
        try {
            PadState currentPad = conversationContext.getPadState();
            currentPad.update(update.deltaPleasure, update.deltaArousal, update.deltaDominance);
            conversationContext.setPadState(currentPad);

            log.info("Mood updated: {} (Delta: P={}, A={}, D={}) | Reasoning: {}",
                Mood.fromPadState(currentPad),
                update.deltaPleasure, update.deltaArousal, update.deltaDominance,
                update.reasoning);

        } catch (Exception e) {
            log.error("Failed to apply mood update", e);
        }
    }

    public Mood getCurrentMood() {
        return Mood.fromPadState(conversationContext.getPadState());
    }
}
