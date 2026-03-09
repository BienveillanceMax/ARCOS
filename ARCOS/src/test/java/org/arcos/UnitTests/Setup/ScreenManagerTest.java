package org.arcos.UnitTests.Setup;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import org.arcos.Setup.StepDefinition;
import org.arcos.Setup.UI.LanternaScreenManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScreenManagerTest {

    private static final List<StepDefinition> ALL_STEPS = List.of(
            StepDefinition.NEXUS, StepDefinition.VOX,
            StepDefinition.ANIMA, StepDefinition.CORPUS,
            StepDefinition.FIAT);

    private Screen screen;

    @BeforeEach
    void setUp() throws IOException {
        DefaultVirtualTerminal vt = new DefaultVirtualTerminal(new TerminalSize(80, 24));
        screen = new TerminalScreen(vt);
        screen.startScreen();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (screen != null) {
            screen.stopScreen();
        }
    }

    @Test
    void screenManager_constructsAndCloses_withoutException() {
        LanternaScreenManager sm = new LanternaScreenManager(screen);
        assertDoesNotThrow(sm::close);
    }

    @Test
    void screenManager_drawFrame_doesNotThrow() {
        LanternaScreenManager sm = new LanternaScreenManager(screen);
        sm.initializeSteps(ALL_STEPS);
        assertDoesNotThrow(sm::drawFrame);
        sm.close();
    }

    @Test
    void screenManager_activateStep_doesNotThrow() {
        LanternaScreenManager sm = new LanternaScreenManager(screen);
        sm.initializeSteps(ALL_STEPS);
        sm.drawFrame();
        assertDoesNotThrow(() -> sm.activateStep(0));
        assertDoesNotThrow(() -> sm.completeStep(0));
        sm.close();
    }

    @Test
    void screenManager_printLine_doesNotThrow() {
        LanternaScreenManager sm = new LanternaScreenManager(screen);
        sm.initializeSteps(ALL_STEPS);
        sm.drawFrame();
        sm.activateStep(0);
        assertDoesNotThrow(() -> sm.printLine("Hello World"));
        assertDoesNotThrow(() -> sm.printLine(0, "Positioned text"));
        assertDoesNotThrow(sm::clearPanel);
        sm.close();
    }

    @Test
    void screenManager_getContentWidth_isPositive() {
        LanternaScreenManager sm = new LanternaScreenManager(screen);
        assertTrue(sm.getContentWidth() > 0);
        assertTrue(sm.isColorSupported());
        sm.close();
    }

    @Test
    void screenManager_close_isIdempotent() {
        LanternaScreenManager sm = new LanternaScreenManager(screen);
        sm.close();
        assertDoesNotThrow(sm::close);
    }

    @Test
    void screenManager_showError_doesNotThrow() {
        LanternaScreenManager sm = new LanternaScreenManager(screen);
        sm.initializeSteps(ALL_STEPS);
        sm.drawFrame();
        sm.activateStep(0);
        assertDoesNotThrow(() -> sm.showError("test error"));
        sm.close();
    }

    @Test
    void screenManager_gaugeCompact_returnsNonEmpty() {
        LanternaScreenManager sm = new LanternaScreenManager(screen);
        String gauge = sm.gaugeCompact("AUT", 85);
        assertNotNull(gauge);
        assertFalse(gauge.isEmpty());
        sm.close();
    }
}
