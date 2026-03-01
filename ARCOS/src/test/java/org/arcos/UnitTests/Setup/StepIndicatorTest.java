package org.arcos.UnitTests.Setup;

import org.arcos.Setup.UI.StepIndicator;
import org.arcos.Setup.UI.StepState;
import org.arcos.Setup.UI.TerminalCapabilities;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StepIndicatorTest {

    @Test
    void indicator_color_active() {
        String result = StepIndicator.indicator(StepIndicator.Status.ACTIVE, true);
        String stripped = TerminalCapabilities.strip(result);
        assertEquals("◆", stripped);
    }

    @Test
    void indicator_color_completed() {
        String result = StepIndicator.indicator(StepIndicator.Status.COMPLETED, true);
        String stripped = TerminalCapabilities.strip(result);
        assertEquals("✓", stripped);
    }

    @Test
    void indicator_color_pending() {
        String result = StepIndicator.indicator(StepIndicator.Status.PENDING, true);
        String stripped = TerminalCapabilities.strip(result);
        assertEquals("◇", stripped);
    }

    @Test
    void indicator_plain_active() {
        assertEquals("[>]", StepIndicator.indicator(StepIndicator.Status.ACTIVE, false));
    }

    @Test
    void indicator_plain_completed() {
        assertEquals("[OK]", StepIndicator.indicator(StepIndicator.Status.COMPLETED, false));
    }

    @Test
    void indicator_plain_pending() {
        assertEquals("[ ]", StepIndicator.indicator(StepIndicator.Status.PENDING, false));
    }

    @Test
    void renderStepIndex_returns2Lines() {
        List<StepState> steps = List.of(
                new StepState("I", "NEXUS", StepIndicator.Status.COMPLETED),
                new StepState("II", "VOX", StepIndicator.Status.COMPLETED),
                new StepState("III", "ANIMA", StepIndicator.Status.ACTIVE),
                new StepState("IV", "CORPUS", StepIndicator.Status.PENDING)
        );

        String[] lines = StepIndicator.renderStepIndex(steps, 60, false);
        assertEquals(2, lines.length);
        assertTrue(lines[0].contains("NEXUS"));
        assertTrue(lines[0].contains("ANIMA"));
        assertTrue(lines[1].contains("VOX"));
        assertTrue(lines[1].contains("CORPUS"));
    }

    @Test
    void renderStepIndex_color_containsIndicators() {
        List<StepState> steps = List.of(
                new StepState("I", "NEXUS", StepIndicator.Status.ACTIVE),
                new StepState("II", "VOX", StepIndicator.Status.PENDING),
                new StepState("III", "ANIMA", StepIndicator.Status.PENDING),
                new StepState("IV", "CORPUS", StepIndicator.Status.PENDING)
        );

        String[] lines = StepIndicator.renderStepIndex(steps, 60, true);
        String stripped0 = TerminalCapabilities.strip(lines[0]);
        assertTrue(stripped0.contains("◆") || stripped0.contains("◇"));
    }

    @Test
    void renderStepIndex_tooFewSteps_throwsException() {
        List<StepState> steps = List.of(
                new StepState("I", "NEXUS", StepIndicator.Status.PENDING)
        );
        assertThrows(IllegalArgumentException.class,
                () -> StepIndicator.renderStepIndex(steps, 60, false));
    }
}
