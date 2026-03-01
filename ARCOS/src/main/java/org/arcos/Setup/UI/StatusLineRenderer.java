package org.arcos.Setup.UI;

/**
 * Renders dot-leader status lines:
 * {@code QDRANT .................... ONLINE      127ms}
 */
public final class StatusLineRenderer {

    private StatusLineRenderer() {}

    /**
     * Renders a status line with dot leaders.
     *
     * @param label       service or config label (e.g., "QDRANT")
     * @param value       status value (e.g., "ONLINE", "DEFICIT")
     * @param detail      optional detail (e.g., "127ms"), null or empty to omit
     * @param statusColor color for the value
     * @param width       total available width
     * @param color       true for ANSI output
     * @return formatted status line
     */
    public static String render(String label, String value, String detail,
                                StatusColor statusColor, int width, boolean color) {
        String detailSuffix = (detail != null && !detail.isEmpty()) ? "  " + detail : "";
        int valueLen = value.length() + detailSuffix.length();
        int dotsNeeded = width - label.length() - valueLen - 2; // 2 spaces around dots
        if (dotsNeeded < 3) dotsNeeded = 3;
        String dots = ".".repeat(dotsNeeded);

        if (color) {
            String detailStr = detail != null && !detail.isEmpty()
                    ? "  " + AnsiPalette.MUTED + detail + AnsiPalette.RESET
                    : "";
            return AnsiPalette.TEXT + label + " " + AnsiPalette.MUTED + dots
                    + AnsiPalette.RESET + " " + statusColor.ansiCode() + value
                    + AnsiPalette.RESET + detailStr;
        }

        return label + " " + dots + " " + value + detailSuffix;
    }
}
