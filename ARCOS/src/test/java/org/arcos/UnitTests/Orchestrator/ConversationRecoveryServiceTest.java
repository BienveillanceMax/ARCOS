package org.arcos.UnitTests.Orchestrator;

import org.arcos.Orchestrator.ConversationRecoveryService;
import org.arcos.Orchestrator.ConversationRecoveryService.Recovery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationRecoveryServiceTest {

    private ConversationRecoveryService recovery;

    @BeforeEach
    void setUp() {
        recovery = new ConversationRecoveryService();
    }

    @Test
    void onUnintelligible_ShouldEscalateThroughThreeLevelsThenGiveUp() {
        // When : trois échecs consécutifs
        Recovery first = recovery.onUnintelligible();
        Recovery second = recovery.onUnintelligible();
        Recovery third = recovery.onUnintelligible();

        // Then : messages tous différents, fenêtre rouverte aux 2 premiers niveaux, fermée au 3e
        assertThat(first.reopenWindow()).isTrue();
        assertThat(second.reopenWindow()).isTrue();
        assertThat(third.reopenWindow()).isFalse();
        assertThat(first.message()).isNotEqualTo(second.message());
        assertThat(second.message()).isNotEqualTo(third.message());
    }

    @Test
    void onUnintelligible_AfterGiveUp_ShouldRestartFromLevelOne() {
        // Given : escalade complète
        recovery.onUnintelligible();
        recovery.onUnintelligible();
        Recovery gaveUp = recovery.onUnintelligible();

        // When
        Recovery next = recovery.onUnintelligible();

        // Then : le compteur est reparti de zéro après l'abandon
        assertThat(next.message()).isNotEqualTo(gaveUp.message());
        assertThat(next.reopenWindow()).isTrue();
    }

    @Test
    void reset_ShouldRestartEscalationFromLevelOne() {
        // Given : un échec puis un énoncé compris
        Recovery first = recovery.onUnintelligible();
        recovery.reset();

        // When
        Recovery afterReset = recovery.onUnintelligible();

        // Then
        assertThat(afterReset.message()).isEqualTo(first.message());
        assertThat(afterReset.reopenWindow()).isTrue();
    }
}
