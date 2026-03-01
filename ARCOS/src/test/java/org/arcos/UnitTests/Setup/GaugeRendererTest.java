package org.arcos.UnitTests.Setup;

import org.arcos.Setup.UI.GaugeRenderer;
import org.arcos.Setup.UI.TerminalCapabilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GaugeRendererTest {

    @Test
    void render_plain_containsLabelAndValue() {
        String result = GaugeRenderer.render("autonomie", 85, 14, false);
        assertTrue(result.contains("autonomie"));
        assertTrue(result.contains("85"));
    }

    @Test
    void render_plain_usesAsciiGauge() {
        String result = GaugeRenderer.render("test", 50, 14, false);
        assertTrue(result.contains("["));
        assertTrue(result.contains("="));
        assertTrue(result.contains("-"));
    }

    @Test
    void render_color_usesHeavyBars() {
        String result = GaugeRenderer.render("test", 80, 14, true);
        String stripped = TerminalCapabilities.strip(result);
        assertTrue(stripped.contains("━"));
    }

    @Test
    void render_zeroValue_allEmpty() {
        String result = GaugeRenderer.render("empty", 0, 14, false);
        assertTrue(result.contains("  0"));
        assertTrue(result.contains("[----------]"));
    }

    @Test
    void render_fullValue_allFilled() {
        String result = GaugeRenderer.render("full", 100, 14, false);
        assertTrue(result.contains("100"));
        assertTrue(result.contains("[==========]"));
    }

    @Test
    void renderCompact_plain_fixedFormat() {
        String result = GaugeRenderer.renderCompact("AUT", 85, false);
        assertTrue(result.contains("AUT"));
        assertTrue(result.contains("85"));
    }

    @Test
    void renderCompact_color_containsGauge() {
        String result = GaugeRenderer.renderCompact("BNV", 90, true);
        String stripped = TerminalCapabilities.strip(result);
        assertTrue(stripped.contains("BNV"));
        assertTrue(stripped.contains("90"));
    }

    @Test
    void render_clampsNegative() {
        String result = GaugeRenderer.render("neg", -10, 14, false);
        assertTrue(result.contains("  0"));
    }

    @Test
    void render_clampsOver100() {
        String result = GaugeRenderer.render("over", 150, 14, false);
        assertTrue(result.contains("100"));
    }
}
