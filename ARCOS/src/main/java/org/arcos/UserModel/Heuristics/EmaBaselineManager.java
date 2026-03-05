package org.arcos.UserModel.Heuristics;

import org.arcos.UserModel.Models.SignificantChange;
import org.arcos.UserModel.Models.TreeBranch;

import java.util.*;

public class EmaBaselineManager {

    private final double alphaColdStart;
    private final double alphaStable;
    private final double significanceThreshold;
    private final int significanceConsecutiveSessions;

    private Map<String, Double> baselines;
    private final Map<String, Integer> consecutiveSignificantCount;
    private int conversationCount;

    public EmaBaselineManager(double alphaColdStart, double alphaStable,
                              double significanceThreshold, int significanceConsecutiveSessions) {
        this.alphaColdStart = alphaColdStart;
        this.alphaStable = alphaStable;
        this.significanceThreshold = significanceThreshold;
        this.significanceConsecutiveSessions = significanceConsecutiveSessions;
        this.baselines = new HashMap<>();
        this.consecutiveSignificantCount = new HashMap<>();
        this.conversationCount = 0;
    }

    public List<SignificantChange> updateBaselines(Map<String, Double> signals, int currentConversationCount) {
        this.conversationCount = currentConversationCount;
        List<SignificantChange> changes = new ArrayList<>();

        for (Map.Entry<String, Double> entry : signals.entrySet()) {
            String signalName = entry.getKey();
            double signalValue = entry.getValue();

            if (!baselines.containsKey(signalName)) {
                baselines.put(signalName, signalValue);
                consecutiveSignificantCount.put(signalName, 0);
                continue;
            }

            double oldBaseline = baselines.get(signalName);
            double alpha = (currentConversationCount < 5) ? alphaColdStart : alphaStable;
            double newBaseline = alpha * signalValue + (1 - alpha) * oldBaseline;

            double denominator = Math.max(Math.abs(oldBaseline), 0.001);
            double relativeDelta = Math.abs(signalValue - oldBaseline) / denominator;

            if (relativeDelta > significanceThreshold) {
                int count = consecutiveSignificantCount.getOrDefault(signalName, 0) + 1;
                consecutiveSignificantCount.put(signalName, count);

                if (count >= significanceConsecutiveSessions) {
                    changes.add(new SignificantChange(
                            signalName,
                            oldBaseline,
                            newBaseline,
                            mapSignalToBranch(signalName)
                    ));
                    consecutiveSignificantCount.put(signalName, 0);
                }
            } else {
                consecutiveSignificantCount.put(signalName, 0);
            }

            baselines.put(signalName, newBaseline);
        }

        return changes;
    }

    public Map<String, Double> getBaselines() {
        return baselines;
    }

    public void setBaselines(Map<String, Double> baselines) {
        this.baselines = new HashMap<>(baselines);
    }

    private TreeBranch mapSignalToBranch(String signalName) {
        return switch (signalName) {
            case "avg_word_count", "vocabulary_diversity", "avg_word_length",
                 "question_ratio", "correction_frequency" -> TreeBranch.COMMUNICATION;
            case "time_of_day" -> TreeBranch.HABITUDES;
            case "initiative_response" -> TreeBranch.INTERETS;
            default -> TreeBranch.HABITUDES;
        };
    }
}
