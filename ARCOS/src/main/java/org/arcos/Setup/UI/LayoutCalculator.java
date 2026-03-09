package org.arcos.Setup.UI;

import com.googlecode.lanterna.TerminalSize;

/**
 * Calculates screen layout zones from terminal dimensions.
 * Content is centered with a max width of ~72 for readability.
 */
public final class LayoutCalculator {

    private static final int MAX_CONTENT_WIDTH = 72;
    private static final int MIN_FRAME_WIDTH = 60;

    private LayoutCalculator() {}

    /**
     * Screen layout zone boundaries (all 0-based row/col indices for Lanterna).
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
     * @param termHeight       terminal height (for boot phases)
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
            int leftMargin,
            int termHeight
    ) {
        public int panelContentHeight() {
            return panelContentEnd - panelContentStart + 1;
        }

        /** Vertical center row for centered content (e.g. banner, spinner). */
        public int verticalCenter() {
            return termHeight / 2;
        }
    }

    /**
     * Calculates layout from Lanterna TerminalSize.
     */
    public static ScreenLayout calculate(TerminalSize size) {
        return calculate(size.getColumns(), size.getRows());
    }

    /**
     * Calculates layout from terminal dimensions.
     * Row indices are 0-based (Lanterna convention).
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

        int headerRow = 0;
        int stepIndexStart = 2;
        int stepIndexEnd = 4;
        int panelDividerRow = 6;
        int panelContentStart = 8;
        int footerRow = termHeight - 1;
        int panelContentEnd = footerRow - 2;

        return new ScreenLayout(
                headerRow,
                stepIndexStart, stepIndexEnd,
                panelDividerRow,
                panelContentStart, panelContentEnd,
                footerRow,
                frameWidth, contentWidth, leftMargin,
                termHeight
        );
    }
}
