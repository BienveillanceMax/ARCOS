package org.arcos.UnitTests.Setup;

import org.arcos.Setup.UI.AnsiPalette;
import org.arcos.Setup.UI.TerminalCapabilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnsiPaletteTest {

    @Test
    void constants_notNull() {
        assertNotNull(AnsiPalette.RESET);
        assertNotNull(AnsiPalette.ORANGE_BRIGHT);
        assertNotNull(AnsiPalette.GREEN);
        assertNotNull(AnsiPalette.RED);
    }

    @Test
    void strip_removesAnsiCodes() {
        // Given
        String colored = AnsiPalette.GREEN + "texte" + AnsiPalette.RESET;

        // When
        String stripped = TerminalCapabilities.strip(colored);

        // Then
        assertEquals("texte", stripped);
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
    void getTerminalWidth_returnsReasonableValue() {
        int width = TerminalCapabilities.getTerminalWidth();
        assertTrue(width >= 20 && width <= 500, "Largeur inattendue : " + width);
    }
}
