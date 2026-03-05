package org.arcos.UnitTests.UserModel;

import org.arcos.UserModel.Heuristics.EmaBaselineManager;
import org.arcos.UserModel.Models.SignificantChange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EmaBaselineManagerTest {

    private EmaBaselineManager manager;

    @BeforeEach
    void setUp() {
        manager = new EmaBaselineManager(0.3, 0.1, 0.20, 3);
    }

    @Test
    void updateBaselines_ColdStart_ShouldUseAlpha03() {
        // Given — initialize baseline for avg_word_count
        manager.updateBaselines(Map.of("avg_word_count", 10.0), 1);
        double initialBaseline = manager.getBaselines().get("avg_word_count");
        assertEquals(10.0, initialBaseline, 0.001);

        // When — update with conversationCount=2 (cold start, alpha=0.3)
        manager.updateBaselines(Map.of("avg_word_count", 20.0), 2);

        // Then — new baseline = 0.3 * 20.0 + 0.7 * 10.0 = 6.0 + 7.0 = 13.0
        double newBaseline = manager.getBaselines().get("avg_word_count");
        assertEquals(13.0, newBaseline, 0.001);
    }

    @Test
    void updateBaselines_Stable_ShouldUseAlpha01() {
        // Given — initialize baseline for avg_word_count
        manager.updateBaselines(Map.of("avg_word_count", 10.0), 1);

        // When — update with conversationCount=10 (stable, alpha=0.1)
        manager.updateBaselines(Map.of("avg_word_count", 20.0), 10);

        // Then — new baseline = 0.1 * 20.0 + 0.9 * 10.0 = 2.0 + 9.0 = 11.0
        double newBaseline = manager.getBaselines().get("avg_word_count");
        assertEquals(11.0, newBaseline, 0.001);
    }

    @Test
    void updateBaselines_Significance3ConsecutiveSessions_ShouldEmitChange() {
        // Given — initialize baseline
        manager.updateBaselines(Map.of("avg_word_count", 10.0), 10);

        // When — 3 consecutive updates with large delta (signal=50, baseline~10 → delta >> 20%)
        List<SignificantChange> changes1 = manager.updateBaselines(Map.of("avg_word_count", 50.0), 10);
        List<SignificantChange> changes2 = manager.updateBaselines(Map.of("avg_word_count", 50.0), 10);
        List<SignificantChange> changes3 = manager.updateBaselines(Map.of("avg_word_count", 50.0), 10);

        // Then — change emitted on 3rd call
        assertTrue(changes1.isEmpty());
        assertTrue(changes2.isEmpty());
        assertEquals(1, changes3.size());
        assertEquals("avg_word_count", changes3.get(0).signalName());
    }

    @Test
    void updateBaselines_NonSignificant_ShouldResetCounter() {
        // Given — initialize baseline
        manager.updateBaselines(Map.of("avg_word_count", 10.0), 10);

        // When — significant, then non-significant (resets counter), then significant 2x
        manager.updateBaselines(Map.of("avg_word_count", 50.0), 10); // significant #1
        manager.updateBaselines(Map.of("avg_word_count", manager.getBaselines().get("avg_word_count")), 10); // non-significant, resets
        manager.updateBaselines(Map.of("avg_word_count", 50.0), 10); // significant #1 again
        List<SignificantChange> changes = manager.updateBaselines(Map.of("avg_word_count", 50.0), 10); // significant #2

        // Then — no change emitted because counter was reset (only 2 consecutive, not 3)
        assertTrue(changes.isEmpty());
    }
}
