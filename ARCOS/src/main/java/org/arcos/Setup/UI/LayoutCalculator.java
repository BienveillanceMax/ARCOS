package org.arcos.Setup.UI;

/**
 * Calculates screen layout zones from terminal dimensions.
 * Content is centered with a max width of ~72 for readability.
 */
public final class LayoutCalculator {

    private static final int MAX_CONTENT_WIDTH = 72;
    private static final int MIN_FRAME_WIDTH = 60;

    private LayoutCalculator() {}

    /**
     * Screen layout zone boundaries (all 1-based row indices).
     *
     * @param headerRow        first row (header bar)
     * @param stepIndexStart   first row of step index strip
     * @param stepIndexEnd     last row of step index strip
     * @param panelDividerRow  the panel divider row
     * @param panelContentStart first row of panel content area
     * @param panelContentEnd   last row of panel content area
     * @param footerRow        footer row
     * @param frameWidth       actual frame width
     * @param contentWidth     usable content width inside borders
     * @param leftMargin       left margin for centering
     */
    public record ScreenLayout(
            int headerRow,
            int stepIndexStart,
            int stepIndexEnd,
            int panelDividerRow,
            int panelContentStart,
            int panelContentEnd,
            int footerRow,
            int frameWidth,
            int contentWidth,
            int leftMargin
    ) {
        public int panelContentHeight() {
            return panelContentEnd - panelContentStart + 1;
        }
    }

    /**
     * Calculates layout from terminal dimensions.
     *
     * @param termWidth  terminal width in columns
     * @param termHeight terminal height in rows
     * @return screen layout with zone boundaries
     */
    public static ScreenLayout calculate(int termWidth, int termHeight) {
        int frameWidth = Math.min(termWidth, MAX_CONTENT_WIDTH + 8);
        frameWidth = Math.max(frameWidth, MIN_FRAME_WIDTH);
        int contentWidth = frameWidth - 6; // 3 left margin + 3 right margin inside borders
        int leftMargin = Math.max(0, (termWidth - frameWidth) / 2);

        // Layout zones (1-based row indices):
        // Row 1: header bar
        // Row 2: empty
        // Row 3-4: step index (2 rows)
        // Row 5: empty
        // Row 6: panel divider
        // Row 7: empty
        // Row 8 to (height-2): panel content
        // Row (height-1): empty
        // Row height: footer
        int headerRow = 1;
        int stepIndexStart = 3;
        int stepIndexEnd = 4;
        int panelDividerRow = 6;
        int panelContentStart = 8;
        int footerRow = termHeight;
        int panelContentEnd = footerRow - 2;

        return new ScreenLayout(
                headerRow,
                stepIndexStart, stepIndexEnd,
                panelDividerRow,
                panelContentStart, panelContentEnd,
                footerRow,
                frameWidth, contentWidth, leftMargin
        );
    }
}
