package org.arcos.UnitTests.Setup;

import org.arcos.Setup.UI.StatusColor;
import org.arcos.Setup.UI.StatusLineRenderer;
import org.arcos.Setup.UI.TerminalCapabilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StatusLineRendererTest {

    @Test
    void render_plain_containsLabelAndValue() {
        String result = StatusLineRenderer.render("QDRANT", "ONLINE", "127ms",
                StatusColor.OK, 50, false);
        assertTrue(result.contains("QDRANT"));
        assertTrue(result.contains("ONLINE"));
        assertTrue(result.contains("127ms"));
        assertTrue(result.contains("..."));
    }

    @Test
    void render_plain_withoutDetail() {
        String result = StatusLineRenderer.render("PIPER TTS", "ONLINE", null,
                StatusColor.OK, 50, false);
        assertTrue(result.contains("PIPER TTS"));
        assertTrue(result.contains("ONLINE"));
        assertFalse(result.contains("null"));
    }

    @Test
    void render_color_containsAnsi() {
        String result = StatusLineRenderer.render("QDRANT", "ONLINE", "127ms",
                StatusColor.OK, 50, true);
        String stripped = TerminalCapabilities.strip(result);
        assertTrue(stripped.contains("QDRANT"));
        assertTrue(stripped.contains("ONLINE"));
    }

    @Test
    void render_deficit_usesBrightColor() {
        String result = StatusLineRenderer.render("WHISPER", "DEFICIT", "timeout",
                StatusColor.BRIGHT, 50, true);
        assertTrue(result.contains(StatusColor.BRIGHT.ansiCode()));
    }

    @Test
    void render_shortWidth_stillWorks() {
        String result = StatusLineRenderer.render("SVC", "OK", null,
                StatusColor.OK, 20, false);
        assertTrue(result.contains("SVC"));
        assertTrue(result.contains("OK"));
    }
}
