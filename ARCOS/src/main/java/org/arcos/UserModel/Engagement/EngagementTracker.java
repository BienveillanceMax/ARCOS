package org.arcos.UserModel.Engagement;

import lombok.extern.slf4j.Slf4j;
import org.arcos.UserModel.UserModelProperties;
import org.arcos.UserModel.UserObservationTree;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
public class EngagementTracker {

    private static final int MAX_HISTORY_SIZE = 50;

    private final UserObservationTree tree;
    private final UserModelProperties properties;

    public EngagementTracker(UserObservationTree tree, UserModelProperties properties) {
        this.tree = tree;
        this.properties = properties;
    }

    public void recordConversation(int messageCount) {
        EngagementRecord record = new EngagementRecord(Instant.now(), messageCount);
        tree.addEngagementRecord(record);
        trimHistory();
        log.debug("Recorded engagement: {} messages, history size={}", messageCount, tree.getEngagementHistory().size());
    }

    public boolean isDecayDetected() {
        if (!properties.getEngagement().isEnabled()) {
            return false;
        }

        List<EngagementRecord> history = tree.getEngagementHistory();
        int minConversations = properties.getEngagement().getMinConversationsForTracking();
        if (history.size() < minConversations) {
            return false;
        }

        int window = properties.getEngagement().getDecayWindowConversations();
        double threshold = properties.getEngagement().getDecayRatioThreshold();

        boolean messageLevelDecay = isMessageLevelDecaying(history, window, threshold);
        boolean frequencyDecay = isFrequencyDecaying(history, window, threshold);

        boolean decay = messageLevelDecay && frequencyDecay;
        if (decay) {
            log.info("Engagement decay detected: message level and frequency both declining");
        }
        return decay;
    }

    public boolean isMessageLevelDecaying(List<EngagementRecord> history, int window, double threshold) {
        double globalAvg = history.stream()
                .mapToInt(EngagementRecord::getMessageCount)
                .average()
                .orElse(0);
        if (globalAvg == 0) return false;

        int recentStart = Math.max(0, history.size() - window);
        double recentAvg = history.subList(recentStart, history.size()).stream()
                .mapToInt(EngagementRecord::getMessageCount)
                .average()
                .orElse(0);

        return recentAvg < globalAvg * threshold;
    }

    public boolean isFrequencyDecaying(List<EngagementRecord> history, int window, double threshold) {
        if (history.size() < window + 1) {
            return false;
        }

        double overallAvgGapHours = averageGapHours(history);
        if (overallAvgGapHours <= 0) return false;

        int recentStart = Math.max(0, history.size() - window);
        List<EngagementRecord> recent = history.subList(recentStart, history.size());
        double recentAvgGapHours = averageGapHours(recent);

        return recentAvgGapHours > overallAvgGapHours / threshold;
    }

    public double averageGapHours(List<EngagementRecord> records) {
        if (records.size() < 2) return 0;
        double totalHours = 0;
        int count = 0;
        for (int i = 1; i < records.size(); i++) {
            Duration gap = Duration.between(records.get(i - 1).getTimestamp(), records.get(i).getTimestamp());
            totalHours += gap.toMinutes() / 60.0;
            count++;
        }
        return count > 0 ? totalHours / count : 0;
    }

    private void trimHistory() {
        List<EngagementRecord> history = tree.getEngagementHistory();
        while (history.size() > MAX_HISTORY_SIZE) {
            history.remove(0);
        }
    }
}
