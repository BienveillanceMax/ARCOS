package org.arcos.UnitTests.UserModel;

import org.arcos.UserModel.Engagement.EngagementRecord;
import org.arcos.UserModel.Engagement.EngagementTracker;
import org.arcos.UserModel.UserModelProperties;
import org.arcos.UserModel.UserObservationTree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EngagementTrackerTest {

    private UserObservationTree tree;
    private UserModelProperties properties;
    private EngagementTracker tracker;

    @BeforeEach
    void setUp() {
        properties = new UserModelProperties();
        properties.getEngagement().setEnabled(true);
        properties.getEngagement().setDecayWindowConversations(3);
        properties.getEngagement().setDecayRatioThreshold(0.5);
        properties.getEngagement().setMinConversationsForTracking(5);
        tree = new UserObservationTree(properties);
        tracker = new EngagementTracker(tree, properties);
    }

    // ---- recordConversation ----

    @Test
    void recordConversation_AddsRecordToHistory() {
        tracker.recordConversation(5);
        assertEquals(1, tree.getEngagementHistory().size());
        assertEquals(5, tree.getEngagementHistory().get(0).getMessageCount());
    }

    @Test
    void recordConversation_TrimsHistoryToMaxSize() {
        for (int i = 0; i < 55; i++) {
            tracker.recordConversation(3);
        }
        assertTrue(tree.getEngagementHistory().size() <= 50);
    }

    // ---- isDecayDetected: not enough data ----

    @Test
    void isDecayDetected_ReturnsFalse_WhenNotEnoughConversations() {
        tracker.recordConversation(5);
        tracker.recordConversation(3);
        assertFalse(tracker.isDecayDetected());
    }

    @Test
    void isDecayDetected_ReturnsFalse_WhenDisabled() {
        properties.getEngagement().setEnabled(false);
        for (int i = 0; i < 10; i++) {
            tracker.recordConversation(1);
        }
        assertFalse(tracker.isDecayDetected());
    }

    // ---- isDecayDetected: stable engagement ----

    @Test
    void isDecayDetected_ReturnsFalse_WhenEngagementIsStable() {
        // Given: 10 conversations with consistent message counts and regular timing
        Instant base = Instant.now().minus(10, ChronoUnit.DAYS);
        for (int i = 0; i < 10; i++) {
            tree.addEngagementRecord(new EngagementRecord(
                    base.plus(i, ChronoUnit.DAYS), 5));
        }
        assertFalse(tracker.isDecayDetected());
    }

    // ---- isDecayDetected: message level decay only ----

    @Test
    void isMessageLevelDecaying_ReturnsTrue_WhenRecentMessagesAreLow() {
        // Given: global avg = 5, recent avg = 1
        Instant base = Instant.now().minus(10, ChronoUnit.DAYS);
        for (int i = 0; i < 7; i++) {
            tree.addEngagementRecord(new EngagementRecord(base.plus(i, ChronoUnit.DAYS), 8));
        }
        for (int i = 0; i < 3; i++) {
            tree.addEngagementRecord(new EngagementRecord(base.plus(7 + i, ChronoUnit.DAYS), 1));
        }

        List<EngagementRecord> history = tree.getEngagementHistory();
        assertTrue(tracker.isMessageLevelDecaying(history, 3, 0.5));
    }

    @Test
    void isMessageLevelDecaying_ReturnsFalse_WhenRecentMessagesAreNormal() {
        Instant base = Instant.now().minus(10, ChronoUnit.DAYS);
        for (int i = 0; i < 10; i++) {
            tree.addEngagementRecord(new EngagementRecord(base.plus(i, ChronoUnit.DAYS), 5));
        }

        List<EngagementRecord> history = tree.getEngagementHistory();
        assertFalse(tracker.isMessageLevelDecaying(history, 3, 0.5));
    }

    // ---- isDecayDetected: frequency decay ----

    @Test
    void isFrequencyDecaying_ReturnsTrue_WhenConversationsBecomeSparse() {
        // Given: first 7 conversations daily, last 3 conversations weekly
        Instant base = Instant.now().minus(50, ChronoUnit.DAYS);
        for (int i = 0; i < 7; i++) {
            tree.addEngagementRecord(new EngagementRecord(base.plus(i, ChronoUnit.DAYS), 5));
        }
        for (int i = 0; i < 3; i++) {
            tree.addEngagementRecord(new EngagementRecord(base.plus(7 + (i * 7), ChronoUnit.DAYS), 5));
        }

        List<EngagementRecord> history = tree.getEngagementHistory();
        assertTrue(tracker.isFrequencyDecaying(history, 3, 0.5));
    }

    @Test
    void isFrequencyDecaying_ReturnsFalse_WhenFrequencyIsConsistent() {
        Instant base = Instant.now().minus(10, ChronoUnit.DAYS);
        for (int i = 0; i < 10; i++) {
            tree.addEngagementRecord(new EngagementRecord(base.plus(i, ChronoUnit.DAYS), 5));
        }

        List<EngagementRecord> history = tree.getEngagementHistory();
        assertFalse(tracker.isFrequencyDecaying(history, 3, 0.5));
    }

    // ---- isDecayDetected: combined detection ----

    @Test
    void isDecayDetected_ReturnsTrue_WhenBothSignalsDecline() {
        // Given: 7 good conversations, then 3 sparse + low-message conversations
        Instant base = Instant.now().minus(50, ChronoUnit.DAYS);
        for (int i = 0; i < 7; i++) {
            tree.addEngagementRecord(new EngagementRecord(base.plus(i, ChronoUnit.DAYS), 8));
        }
        for (int i = 0; i < 3; i++) {
            tree.addEngagementRecord(new EngagementRecord(base.plus(7 + (i * 7), ChronoUnit.DAYS), 1));
        }

        assertTrue(tracker.isDecayDetected());
    }

    @Test
    void isDecayDetected_ReturnsFalse_WhenOnlyMessageLevelDeclines() {
        // Given: frequency is consistent but message count drops
        Instant base = Instant.now().minus(10, ChronoUnit.DAYS);
        for (int i = 0; i < 7; i++) {
            tree.addEngagementRecord(new EngagementRecord(base.plus(i, ChronoUnit.DAYS), 8));
        }
        for (int i = 0; i < 3; i++) {
            tree.addEngagementRecord(new EngagementRecord(base.plus(7 + i, ChronoUnit.DAYS), 1));
        }

        // Frequency is still daily, so only one signal → false (requires both)
        assertFalse(tracker.isDecayDetected());
    }

    // ---- averageGapHours ----

    @Test
    void averageGapHours_CalculatesCorrectly() {
        Instant base = Instant.now();
        List<EngagementRecord> records = List.of(
                new EngagementRecord(base, 3),
                new EngagementRecord(base.plus(24, ChronoUnit.HOURS), 3),
                new EngagementRecord(base.plus(48, ChronoUnit.HOURS), 3)
        );
        assertEquals(24.0, tracker.averageGapHours(records), 0.1);
    }

    @Test
    void averageGapHours_ReturnsZero_ForSingleRecord() {
        List<EngagementRecord> records = List.of(new EngagementRecord(Instant.now(), 3));
        assertEquals(0, tracker.averageGapHours(records));
    }
}
