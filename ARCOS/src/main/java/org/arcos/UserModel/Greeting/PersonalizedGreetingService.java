package org.arcos.UserModel.Greeting;

import lombok.extern.slf4j.Slf4j;
import org.arcos.UserModel.Engagement.EngagementRecord;
import org.arcos.UserModel.Models.TreeBranch;
import org.arcos.UserModel.UserObservationTree;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class PersonalizedGreetingService {

    private final UserObservationTree tree;

    public PersonalizedGreetingService(UserObservationTree tree) {
        this.tree = tree;
    }

    public Optional<String> buildGreetingContext() {
        return buildGreetingContext(LocalTime.now(), Instant.now());
    }

    public Optional<String> buildGreetingContext(LocalTime timeOfDay, Instant now) {
        int conversationCount = tree.getConversationCount();
        if (conversationCount < 3) {
            return Optional.empty();
        }

        StringBuilder context = new StringBuilder();

        appendTimeOfDay(context, timeOfDay);
        appendDaysSinceLastConversation(context, now);
        appendBranchSummary(context, TreeBranch.OBJECTIFS, "Objectifs actuels");
        appendBranchSummary(context, TreeBranch.HABITUDES, "Habitudes connues");
        appendBranchSummary(context, TreeBranch.IDENTITE, "Identité");

        String result = context.toString().trim();
        return result.isEmpty() ? Optional.empty() : Optional.of(result);
    }

    private void appendTimeOfDay(StringBuilder context, LocalTime timeOfDay) {
        if (timeOfDay.isBefore(LocalTime.of(12, 0))) {
            context.append("C'est le matin. ");
        } else if (timeOfDay.isBefore(LocalTime.of(18, 0))) {
            context.append("C'est l'après-midi. ");
        } else {
            context.append("C'est le soir. ");
        }
    }

    private void appendDaysSinceLastConversation(StringBuilder context, Instant now) {
        List<EngagementRecord> history = tree.getEngagementHistory();
        if (!history.isEmpty()) {
            Instant last = history.get(history.size() - 1).getTimestamp();
            long daysSince = Duration.between(last, now).toDays();
            if (daysSince >= 2) {
                context.append("L'utilisateur n'a pas été vu depuis ")
                        .append(daysSince).append(" jours. ");
            }
        }
    }

    private void appendBranchSummary(StringBuilder context, TreeBranch branch, String label) {
        String summary = tree.getSummary(branch);
        if (summary != null && !summary.isBlank()) {
            context.append(label).append(" : ").append(summary).append(". ");
        }
    }
}
