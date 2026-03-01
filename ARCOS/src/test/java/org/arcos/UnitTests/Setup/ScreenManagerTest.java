package org.arcos.UnitTests.Setup;

import org.arcos.Setup.StepDefinition;
import org.arcos.Setup.UI.ScreenManager;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScreenManagerTest {

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
    void screenManager_constructsAndCloses_withoutException() throws IOException {
        // Given
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Terminal terminal = buildDumbTerminal(baos);

        // When/Then — constructor enters alt screen, close exits it
        ScreenManager sm = new ScreenManager(terminal);
        assertDoesNotThrow(sm::close);
        terminal.close();
    }

    @Test
    void screenManager_drawFrame_doesNotThrow() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Terminal terminal = buildDumbTerminal(baos);

        ScreenManager sm = new ScreenManager(terminal);
        sm.initializeSteps(ALL_STEPS);
        assertDoesNotThrow(sm::drawFrame);
        sm.close();
        terminal.close();
    }

    @Test
    void screenManager_activateStep_doesNotThrow() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Terminal terminal = buildDumbTerminal(baos);

        ScreenManager sm = new ScreenManager(terminal);
        sm.initializeSteps(ALL_STEPS);
        sm.drawFrame();
        assertDoesNotThrow(() -> sm.activateStep(0));
        assertDoesNotThrow(() -> sm.completeStep(0));
        sm.close();
        terminal.close();
    }

    @Test
    void screenManager_printLine_doesNotThrow() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Terminal terminal = buildDumbTerminal(baos);

        ScreenManager sm = new ScreenManager(terminal);
        sm.initializeSteps(ALL_STEPS);
        sm.drawFrame();
        sm.activateStep(0);
        assertDoesNotThrow(() -> sm.printLine("Hello World"));
        assertDoesNotThrow(() -> sm.printLine(0, "Positioned text"));
        assertDoesNotThrow(sm::clearPanel);
        sm.close();
        terminal.close();
    }

    @Test
    void screenManager_getContentWidth_isPositive() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Terminal terminal = buildDumbTerminal(baos);

        ScreenManager sm = new ScreenManager(terminal);
        assertTrue(sm.getContentWidth() > 0);
        assertTrue(sm.isColorSupported());
        sm.close();
        terminal.close();
    }

    @Test
    void screenManager_close_isIdempotent() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Terminal terminal = buildDumbTerminal(baos);

        ScreenManager sm = new ScreenManager(terminal);
        sm.close();
        assertDoesNotThrow(sm::close);
        terminal.close();
    }

    @Test
    void screenManager_showError_doesNotThrow() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Terminal terminal = buildDumbTerminal(baos);

        ScreenManager sm = new ScreenManager(terminal);
        sm.initializeSteps(ALL_STEPS);
        sm.drawFrame();
        sm.activateStep(0);
        assertDoesNotThrow(() -> sm.showError("test error"));
        sm.close();
        terminal.close();
    }

    @Test
    void screenManager_gaugeCompact_returnsNonEmpty() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Terminal terminal = buildDumbTerminal(baos);

        ScreenManager sm = new ScreenManager(terminal);
        String gauge = sm.gaugeCompact("AUT", 85);
        assertNotNull(gauge);
        assertFalse(gauge.isEmpty());
        sm.close();
        terminal.close();
    }
}
