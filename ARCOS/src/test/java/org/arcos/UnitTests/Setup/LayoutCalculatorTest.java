package org.arcos.UnitTests.Setup;

import org.arcos.Setup.UI.LayoutCalculator;
import org.arcos.Setup.UI.LayoutCalculator.ScreenLayout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LayoutCalculatorTest {

    @Test
    void calculate_80x24_returnsValidLayout() {
        // Given — standard 80x24 terminal
        ScreenLayout layout = LayoutCalculator.calculate(80, 24);

        // Then
        assertEquals(1, layout.headerRow());
        assertEquals(3, layout.stepIndexStart());
        assertEquals(4, layout.stepIndexEnd());
        assertEquals(6, layout.panelDividerRow());
        assertEquals(8, layout.panelContentStart());
        assertEquals(24, layout.footerRow());
        assertTrue(layout.panelContentEnd() < layout.footerRow());
        assertTrue(layout.contentWidth() > 0);
        assertTrue(layout.frameWidth() >= 60);
    }

    @Test
    void calculate_wideTerminal_capsFrameWidth() {
        // Given — very wide terminal
        ScreenLayout layout = LayoutCalculator.calculate(200, 30);

        // Then — frame width capped at MAX_CONTENT_WIDTH + 8 = 80
        assertTrue(layout.frameWidth() <= 80);
        assertTrue(layout.leftMargin() > 0);
    }

    @Test
    void calculate_minTerminal_usesMinFrameWidth() {
        // Given — narrow terminal
        ScreenLayout layout = LayoutCalculator.calculate(60, 20);

        // Then
        assertEquals(60, layout.frameWidth());
        assertEquals(0, layout.leftMargin());
    }

    @Test
    void panelContentHeight_isPositive() {
        ScreenLayout layout = LayoutCalculator.calculate(80, 24);
        assertTrue(layout.panelContentHeight() > 0);
    }

    @Test
    void calculate_tallTerminal_givesMorePanelSpace() {
        ScreenLayout small = LayoutCalculator.calculate(80, 24);
        ScreenLayout tall = LayoutCalculator.calculate(80, 40);

        assertTrue(tall.panelContentHeight() > small.panelContentHeight());
    }
}
