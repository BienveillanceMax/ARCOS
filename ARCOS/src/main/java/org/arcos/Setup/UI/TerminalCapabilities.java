package org.arcos.Setup.UI;

import org.jline.terminal.Terminal;

/**
 * Terminal capability detection for ARCOS TUI.
 * Used to select full-screen vs fallback rendering mode.
 */
public final class TerminalCapabilities {

    static final int MIN_WIDTH = 60;
    static final int MIN_HEIGHT = 20;

    private TerminalCapabilities() {}

    /**
     * Returns true if the terminal supports ANSI colors.
     * False if TERM=dumb, no TTY, or CLICOLOR=false.
     */
    public static boolean isColorSupported() {
        if ("false".equalsIgnoreCase(System.getenv("CLICOLOR"))) {
            return false;
        }
        if ("dumb".equalsIgnoreCase(System.getenv("TERM"))) {
            return false;
        }
        return System.console() != null;
    }

    /**
     * Returns true if the terminal supports full-screen alternate buffer mode.
     * False for TERM=dumb, null, or "linux" (virtual console where box-drawing may not render).
     */
    public static boolean isFullScreenSupported() {
        String term = System.getenv("TERM");
        if (term == null || term.isEmpty()) return false;
        if ("dumb".equalsIgnoreCase(term)) return false;
        if ("linux".equalsIgnoreCase(term)) return false;
        return System.console() != null;
    }

    /**
     * Returns true if the terminal width meets the minimum (60 columns).
     */
    public static boolean isMinimumWidthMet(Terminal terminal) {
        return terminal.getWidth() >= MIN_WIDTH;
    }

    /**
     * Returns true if the terminal height meets the minimum (20 rows).
     */
    public static boolean isMinimumHeightMet(Terminal terminal) {
        return terminal.getHeight() >= MIN_HEIGHT;
    }

    /**
     * Returns the terminal width, or 80 by default.
     */
    public static int getTerminalWidth() {
        String columns = System.getenv("COLUMNS");
        if (columns != null) {
            try {
                int w = Integer.parseInt(columns.trim());
                if (w > 20 && w < 500) return w;
            } catch (NumberFormatException ignored) {}
        }
        return 80;
    }

    /**
     * Strips ANSI escape codes from a string, including color codes,
     * cursor positioning, and screen control sequences.
     */
    public static String strip(String text) {
        if (text == null) return null;
        return text.replaceAll("\033\\[[0-9;]*[mHJKhlf]", "")
                   .replaceAll("\033\\[\\?[0-9;]*[hl]", "");
    }

    /**
     * Applies color only if supported.
     */
    public static String colorize(String ansiCode, String text) {
        if (isColorSupported()) {
            return ansiCode + text + AnsiPalette.RESET;
        }
        return text;
    }
}
