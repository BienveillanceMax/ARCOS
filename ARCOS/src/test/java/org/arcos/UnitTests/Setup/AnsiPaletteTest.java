package org.arcos.UnitTests.Setup;

import org.arcos.Setup.UI.AnsiPalette;
import org.arcos.Setup.UI.TerminalCapabilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnsiPaletteTest {

    @Test
    void newConstants_notNull() {
        assertNotNull(AnsiPalette.BRIGHT);
        assertNotNull(AnsiPalette.PRIMARY);
        assertNotNull(AnsiPalette.DEEP);
        assertNotNull(AnsiPalette.TEXT);
        assertNotNull(AnsiPalette.MUTED);
        assertNotNull(AnsiPalette.DIM);
        assertNotNull(AnsiPalette.OK);
        assertNotNull(AnsiPalette.WARN);
        assertNotNull(AnsiPalette.INFO);
    }

    @Test
    void deprecatedAliases_matchNewConstants() {
        assertSame(AnsiPalette.PRIMARY, AnsiPalette.ORANGE_BRIGHT);
        assertSame(AnsiPalette.BRIGHT, AnsiPalette.AMBER);
        assertSame(AnsiPalette.DEEP, AnsiPalette.ORANGE_DARK);
        assertSame(AnsiPalette.OK, AnsiPalette.GREEN);
        assertSame(AnsiPalette.BRIGHT, AnsiPalette.RED);
        assertSame(AnsiPalette.WARN, AnsiPalette.YELLOW);
        assertSame(AnsiPalette.INFO, AnsiPalette.CYAN);
        assertSame(AnsiPalette.MUTED, AnsiPalette.GRAY_LIGHT);
        assertSame(AnsiPalette.TEXT, AnsiPalette.WHITE);
    }

    @Test
    void constants_containCorrectAnsiCodes() {
        assertTrue(AnsiPalette.BRIGHT.contains("196"), "BRIGHT should use color 196");
        assertTrue(AnsiPalette.PRIMARY.contains("160"), "PRIMARY should use color 160");
        assertTrue(AnsiPalette.DEEP.contains("52"), "DEEP should use color 52");
        assertTrue(AnsiPalette.TEXT.contains("252"), "TEXT should use color 252");
        assertTrue(AnsiPalette.OK.contains("71"), "OK should use color 71");
        assertTrue(AnsiPalette.WARN.contains("178"), "WARN should use color 178");
        assertTrue(AnsiPalette.INFO.contains("67"), "INFO should use color 67");
    }

    @Test
    void strip_removesAnsiCodes() {
        String colored = AnsiPalette.OK + "texte" + AnsiPalette.RESET;
        assertEquals("texte", TerminalCapabilities.strip(colored));
    }

    @Test
    void strip_handlesNull() {
        assertNull(TerminalCapabilities.strip(null));
    }

    @Test
    void strip_handlesPlainText() {
        assertEquals("hello", TerminalCapabilities.strip("hello"));
    }

    @Test
    void strip_removesCursorPositioning() {
        String withCursor = "\033[5;10H" + "text" + "\033[2K";
        assertEquals("text", TerminalCapabilities.strip(withCursor));
    }

    @Test
    void strip_removesScreenControl() {
        String withControl = "\033[?1049h" + "content" + "\033[?1049l";
        assertEquals("content", TerminalCapabilities.strip(withControl));
    }

    @Test
    void getTerminalWidth_returnsReasonableValue() {
        int width = TerminalCapabilities.getTerminalWidth();
        assertTrue(width >= 20 && width <= 500, "Unexpected width: " + width);
    }
}
