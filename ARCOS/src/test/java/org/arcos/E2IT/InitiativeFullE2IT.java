package org.arcos.E2IT;

import org.arcos.EventBus.Events.Event;
import org.arcos.EventBus.Events.EventType;
import org.arcos.Memory.LongTermMemory.Models.DesireEntry;
import org.arcos.Personality.Desires.DesireService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E — Sprint 10 E4: Autonomous initiative.
 *
 * Proves the full initiative chain through the Orchestrator:
 *   DesireEntry PENDING in Qdrant → INITIATIVE event dispatched to Orchestrator
 *   → InitiativeService.processInitiative() → enrichment (memories, opinions)
 *   → LLM (Mistral) generates initiative → TTS output related to desire.
 *
 * The LLM may [SKIP] the initiative if it judges the desire non-actionable.
 * This is a valid outcome, not a test failure — we document it.
 *
 * Prerequisites: Qdrant, Mistral API.
 */
@Tag("e2e")
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class InitiativeFullE2IT extends BaseE2IT {

    @Autowired private DesireService desireService;

    private DesireEntry createAndStoreDesire(String label, String description, double intensity) {
        DesireEntry desire = new DesireEntry();
        desire.setId(UUID.randomUUID().toString());
        desire.setLabel(label);
        desire.setDescription(description);
        desire.setIntensity(intensity);
        desire.setStatus(DesireEntry.Status.PENDING);
        desire.setCreatedAt(LocalDateTime.now());
        desire.setLastUpdated(LocalDateTime.now());
        desireService.storeDesire(desire);
        return desire;
    }

    @Test
    void t1_initiativeEventProducesSpeechRelatedToDesire() {
        DesireEntry desire = createAndStoreDesire(
            "Partager une recette de cuisine italienne",
            "Envie de parler de cuisine italienne et partager une recette de pâtes",
            0.85);

        // Dispatch INITIATIVE through the Orchestrator — full chain
        orchestrator.dispatch(new Event<>(EventType.INITIATIVE, desire, "test"));

        Awaitility.await().atMost(Duration.ofSeconds(30))
            .until(() -> mockTTS.hasSpoken());

        String allSpoken = String.join(" ", mockTTS.getSpokenTexts());
        assertFalse(allSpoken.isBlank(), "Initiative should produce TTS output");

        // The LLM may have [SKIP]ped. If not, the output should relate to the desire.
        if (!allSpoken.contains("[SKIP]")) {
            String lower = allSpoken.toLowerCase();
            assertTrue(
                lower.matches(".*(?:cuisine|recette|pâtes|italien|culinaire|plat|manger).*"),
                "Initiative speech should relate to the desire subject (cuisine), got: " + allSpoken);
        }
    }

    @Test
    void t2_initiativeUpdatesDesireStatus() {
        DesireEntry desire = createAndStoreDesire(
            "Découvrir de nouvelles musiques jazz",
            "Explorer de nouveaux artistes de jazz et en parler",
            0.9);

        String desireId = desire.getId();

        orchestrator.dispatch(new Event<>(EventType.INITIATIVE, desire, "test"));

        Awaitility.await().atMost(Duration.ofSeconds(30))
            .until(() -> mockTTS.hasSpoken());

        // After a successful initiative, the desire should no longer be PENDING
        boolean stillPending = desireService.getPendingDesires().stream()
            .anyMatch(d -> desireId.equals(d.getId()));

        String allSpoken = String.join(" ", mockTTS.getSpokenTexts());
        if (!allSpoken.contains("[SKIP]")) {
            assertFalse(stillPending,
                "After successful initiative, desire should no longer be PENDING");
        }
        // If [SKIP], desire stays PENDING — that's valid behavior
    }
}
