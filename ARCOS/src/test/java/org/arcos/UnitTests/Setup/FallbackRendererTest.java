package org.arcos.UnitTests.Setup;

import org.arcos.Setup.StepDefinition;
import org.arcos.Setup.UI.FallbackRenderer;
import org.arcos.Setup.UI.StatusColor;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FallbackRendererTest {

    private static final List<StepDefinition> ALL_STEPS = List.of(
            StepDefinition.NEXUS, StepDefinition.VOX,
            StepDefinition.ANIMA, StepDefinition.CORPUS);

    private Terminal buildDumbTerminal(ByteArrayOutputStream baos) throws IOException {
        return TerminalBuilder.builder()
                .type("dumb")
                .streams(System.in, baos)
                .build();
    }

    @Test
    void fallbackRenderer_noAlternateScreenSequences() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Terminal terminal = buildDumbTerminal(baos);

        FallbackRenderer fb = new FallbackRenderer(terminal);
        fb.initializeSteps(ALL_STEPS);
        fb.drawFrame();
        fb.activateStep(0);
        fb.printLine("Hello fallback");
        fb.close();
        terminal.close();

        String output = baos.toString();
        assertFalse(output.contains("\033[?1049h"), "Fallback should not use alternate screen");
    }

    @Test
    void fallbackRenderer_drawFrame_doesNotThrow() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Terminal terminal = buildDumbTerminal(baos);

        FallbackRenderer fb = new FallbackRenderer(terminal);
        fb.initializeSteps(ALL_STEPS);
        assertDoesNotThrow(fb::drawFrame);
        fb.close();
        terminal.close();
    }

    @Test
    void fallbackRenderer_activateStep_doesNotThrow() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Terminal terminal = buildDumbTerminal(baos);

        FallbackRenderer fb = new FallbackRenderer(terminal);
        fb.initializeSteps(ALL_STEPS);
        fb.drawFrame();
        assertDoesNotThrow(() -> fb.activateStep(0));
        assertDoesNotThrow(() -> fb.completeStep(0));
        fb.close();
        terminal.close();
    }

    @Test
    void fallbackRenderer_printLine_doesNotThrow() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Terminal terminal = buildDumbTerminal(baos);

        FallbackRenderer fb = new FallbackRenderer(terminal);
        assertDoesNotThrow(() -> fb.printLine("Test content"));
        assertDoesNotThrow(() -> fb.printLine(0, "Positioned content"));
        assertDoesNotThrow(fb::clearPanel);
        fb.close();
        terminal.close();
    }

    @Test
    void fallbackRenderer_statusLine_doesNotThrow() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Terminal terminal = buildDumbTerminal(baos);

        FallbackRenderer fb = new FallbackRenderer(terminal);
        assertDoesNotThrow(() -> fb.statusLine("QDRANT", "ONLINE", "127ms", StatusColor.OK));
        assertDoesNotThrow(() -> fb.gauge("test", 50, 14));
        fb.close();
        terminal.close();
    }

    @Test
    void fallbackRenderer_getContentWidth_isPositive() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Terminal terminal = buildDumbTerminal(baos);

        FallbackRenderer fb = new FallbackRenderer(terminal);
        assertTrue(fb.getContentWidth() > 0);
        fb.close();
        terminal.close();
    }

    @Test
    void fallbackRenderer_showError_doesNotThrow() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Terminal terminal = buildDumbTerminal(baos);

        FallbackRenderer fb = new FallbackRenderer(terminal);
        assertDoesNotThrow(() -> fb.showError("test error"));
        fb.close();
        terminal.close();
    }

    @Test
    void fallbackRenderer_gaugeCompact_returnsNonEmpty() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Terminal terminal = buildDumbTerminal(baos);

        FallbackRenderer fb = new FallbackRenderer(terminal);
        String gauge = fb.gaugeCompact("AUT", 85);
        assertNotNull(gauge);
        assertFalse(gauge.isEmpty());
        fb.close();
        terminal.close();
    }
}
