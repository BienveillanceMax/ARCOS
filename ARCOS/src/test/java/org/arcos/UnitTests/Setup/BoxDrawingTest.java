package org.arcos.UnitTests.Setup;

import org.arcos.Setup.UI.BoxDrawing;
import org.arcos.Setup.UI.TerminalCapabilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoxDrawingTest {

    @Test
    void headerBar_plain_containsArcos() {
        String result = BoxDrawing.headerBar(60, true);
        assertTrue(result.contains("ARCOS"));
        assertTrue(result.contains("INITIUM"));
        assertTrue(result.startsWith("+"));
        assertTrue(result.endsWith("+"));
    }

    @Test
    void headerBar_color_containsArcos() {
        String result = BoxDrawing.headerBar(60, false);
        String stripped = TerminalCapabilities.strip(result);
        assertTrue(stripped.contains("ARCOS"));
        assertTrue(stripped.contains("INITIUM"));
    }

    @Test
    void panelDivider_plain_containsLabel() {
        String result = BoxDrawing.panelDivider("III", "ANIMA", 60, true);
        assertTrue(result.contains("III // ANIMA"));
        assertTrue(result.startsWith("+"));
    }

    @Test
    void panelDivider_noNumeral_omitsSlashes() {
        String result = BoxDrawing.panelDivider("", "FIAT", 60, true);
        assertTrue(result.contains("FIAT"));
        assertFalse(result.contains("//"));
    }

    @Test
    void panelDivider_color_usesBoxChars() {
        String result = BoxDrawing.panelDivider("I", "NEXUS", 60, false);
        String stripped = TerminalCapabilities.strip(result);
        assertTrue(stripped.contains("┣"));
        assertTrue(stripped.contains("┫"));
    }

    @Test
    void footer_plain_usesAscii() {
        String result = BoxDrawing.footer(60, true);
        assertTrue(result.startsWith("+"));
        assertTrue(result.endsWith("+"));
        assertTrue(result.contains("-"));
    }

    @Test
    void footer_color_usesBoxChars() {
        String result = BoxDrawing.footer(60, false);
        String stripped = TerminalCapabilities.strip(result);
        assertTrue(stripped.startsWith("┗"));
        assertTrue(stripped.endsWith("┛"));
    }

    @Test
    void emptyRow_hasCorrectWidth() {
        String result = BoxDrawing.emptyRow(60, true);
        assertEquals(60, result.length());
    }

    @Test
    void sideBorder_plain_isPipe() {
        assertEquals("|", BoxDrawing.sideBorder(true));
    }

    @Test
    void contentRow_plain_containsText() {
        String result = BoxDrawing.contentRow("Hello world", 60, true);
        assertTrue(result.contains("Hello world"));
        assertTrue(result.startsWith("|"));
        assertTrue(result.endsWith("|"));
    }
}
