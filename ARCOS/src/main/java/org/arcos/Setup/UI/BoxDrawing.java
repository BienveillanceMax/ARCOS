package org.arcos.Setup.UI;

/**
 * Box drawing primitives for the wizard TUI frame.
 * Heavy box characters (┏━┓┃┗━┛┣┫) for color mode, ASCII (+, -, |) for plain.
 */
public final class BoxDrawing {

    private BoxDrawing() {}

    /**
     * Top header bar: ┏━━ ARCOS ━━━━━━━━━━━━━━━━ INITIUM v1.0 ━━━━┓
     */
    public static String headerBar(int width, boolean plain) {
        if (plain) {
            String leftLabel = "-- ARCOS ";
            String rightLabel = " INITIUM v1.0 --";
            int fillLen = width - 2 - leftLabel.length() - rightLabel.length();
            if (fillLen < 0) fillLen = 0;
            return "+" + leftLabel + "-".repeat(fillLen) + rightLabel + "+";
        }
        String leftLabel = " ARCOS ";
        String rightLabel = " INITIUM v1.0 ";
        int fillLeft = 2;
        int fillRight = 4;
        int fillMid = width - 2 - fillLeft - leftLabel.length() - fillRight - rightLabel.length();
        if (fillMid < 0) fillMid = 0;
        return AnsiPalette.PRIMARY + "┏" + "━".repeat(fillLeft) + AnsiPalette.BRIGHT + leftLabel
                + AnsiPalette.PRIMARY + "━".repeat(fillMid) + AnsiPalette.MUTED + rightLabel
                + AnsiPalette.PRIMARY + "━".repeat(fillRight) + "┓" + AnsiPalette.RESET;
    }

    /**
     * Panel divider: ┣━━ III // ANIMA ━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫
     */
    public static String panelDivider(String romanNumeral, String latinName, int width, boolean plain) {
        String label;
        if (romanNumeral == null || romanNumeral.isEmpty()) {
            label = " " + latinName + " ";
        } else {
            label = " " + romanNumeral + " // " + latinName + " ";
        }

        if (plain) {
            int fillLen = width - 2 - label.length();
            if (fillLen < 0) fillLen = 0;
            return "+" + label + "-".repeat(fillLen) + "+";
        }
        int fillLen = width - 2 - label.length();
        if (fillLen < 0) fillLen = 0;
        return AnsiPalette.PRIMARY + "┣" + "━".repeat(1)
                + AnsiPalette.BRIGHT + label
                + AnsiPalette.PRIMARY + "━".repeat(Math.max(0, fillLen - 1)) + "┫" + AnsiPalette.RESET;
    }

    /**
     * Bottom footer bar.
     */
    public static String footer(int width, boolean plain) {
        if (plain) {
            return "+" + "-".repeat(width - 2) + "+";
        }
        return AnsiPalette.PRIMARY + "┗" + "━".repeat(width - 2) + "┛" + AnsiPalette.RESET;
    }

    /**
     * Side border character for content rows.
     */
    public static String sideBorder(boolean plain) {
        if (plain) return "|";
        return AnsiPalette.PRIMARY + "┃" + AnsiPalette.RESET;
    }

    /**
     * Empty content row with side borders.
     */
    public static String emptyRow(int width, boolean plain) {
        String border = sideBorder(plain);
        return border + " ".repeat(width - 2) + border;
    }

    /**
     * Content row with text and side borders.
     * Text is left-aligned inside the frame with 3-char left margin.
     */
    public static String contentRow(String text, int width, boolean plain) {
        String border = sideBorder(plain);
        int maxVisible = width - 2 - 3 - 1; // borders + left margin + min 1 right padding
        int visibleTextLen = TerminalCapabilities.strip(text).length();
        if (visibleTextLen > maxVisible) {
            text = truncateToVisible(text, maxVisible);
            visibleTextLen = maxVisible;
        }
        int paddingRight = width - 2 - 3 - visibleTextLen;
        if (paddingRight < 1) paddingRight = 1;
        return border + "   " + text + " ".repeat(paddingRight) + border;
    }

    /**
     * Truncates a string (possibly containing ANSI codes) to a maximum visible width.
     */
    private static String truncateToVisible(String text, int maxVisible) {
        String stripped = TerminalCapabilities.strip(text);
        if (stripped.length() <= maxVisible) return text;
        // Walk the original string tracking visible characters
        StringBuilder result = new StringBuilder();
        int visible = 0;
        int i = 0;
        while (i < text.length() && visible < maxVisible) {
            if (text.charAt(i) == '\033') {
                // Copy entire ANSI escape sequence
                int seqStart = i;
                i++;
                while (i < text.length() && !Character.isLetter(text.charAt(i))) i++;
                if (i < text.length()) i++; // include the terminating letter
                result.append(text, seqStart, i);
            } else {
                result.append(text.charAt(i));
                visible++;
                i++;
            }
        }
        result.append(AnsiPalette.RESET);
        return result.toString();
    }
}
