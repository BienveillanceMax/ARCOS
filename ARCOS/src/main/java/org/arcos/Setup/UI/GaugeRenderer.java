package org.arcos.Setup.UI;

/**
 * Renders thin-track gauges for trait visualization.
 * Filled: ━ (heavy horizontal) in PRIMARY. Empty: ─ (light horizontal) in MUTED.
 * Scale: 10 segments, each represents 10 units (0-100).
 */
public final class GaugeRenderer {

    private static final int GAUGE_SEGMENTS = 10;
    private static final char FILLED = '━';
    private static final char EMPTY = '─';
    private static final char FILLED_PLAIN = '=';
    private static final char EMPTY_PLAIN = '-';

    private GaugeRenderer() {}

    /**
     * Full-line gauge: {@code label ━━━━━━━━──  85}
     *
     * @param label      trait label (left-aligned)
     * @param value      0-100
     * @param labelWidth fixed label column width
     * @param color      true for ANSI output
     * @return formatted gauge line
     */
    public static String render(String label, int value, int labelWidth, boolean color) {
        String paddedLabel = padRight(label, labelWidth);
        String gauge = buildGauge(value, color);
        String valueStr = String.format("%3d", clamp(value));

        if (color) {
            return AnsiPalette.TEXT + paddedLabel + AnsiPalette.RESET + "  " + gauge + "  "
                    + AnsiPalette.MUTED + valueStr + AnsiPalette.RESET;
        }
        return paddedLabel + "  " + gauge + "  " + valueStr;
    }

    /**
     * Compact horizontal gauge: {@code AUT ━━━━━━━━── 85}
     * Fixed ~18-char width for side-by-side layout.
     *
     * @param abbreviation 3-letter trait abbreviation
     * @param value        0-100
     * @param color        true for ANSI output
     * @return fixed-width compact gauge string
     */
    public static String renderCompact(String abbreviation, int value, boolean color) {
        String gauge = buildGauge(value, color);
        String valueStr = String.format("%3d", clamp(value));
        String abbr = padRight(abbreviation, 3);

        if (color) {
            return AnsiPalette.MUTED + abbr + AnsiPalette.RESET + " " + gauge + " "
                    + AnsiPalette.MUTED + valueStr + AnsiPalette.RESET;
        }
        return abbr + " " + gauge + " " + valueStr;
    }

    private static String buildGauge(int value, boolean color) {
        int filled = Math.round(clamp(value) / 10.0f);
        int empty = GAUGE_SEGMENTS - filled;

        if (color) {
            return AnsiPalette.PRIMARY + repeat(FILLED, filled)
                    + AnsiPalette.MUTED + repeat(EMPTY, empty)
                    + AnsiPalette.RESET;
        }
        return "[" + repeat(FILLED_PLAIN, filled) + repeat(EMPTY_PLAIN, empty) + "]";
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static String padRight(String s, int width) {
        if (s.length() >= width) return s;
        return s + " ".repeat(width - s.length());
    }

    private static String repeat(char c, int count) {
        if (count <= 0) return "";
        return String.valueOf(c).repeat(count);
    }
}
