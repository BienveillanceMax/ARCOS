package org.arcos.Setup.UI;

import java.util.List;

/**
 * Renders step progress indicators and the two-row step index strip.
 */
public final class StepIndicator {

    private StepIndicator() {}

    public enum Status { ACTIVE, COMPLETED, PENDING }

    /**
     * Returns the indicator symbol for a step status.
     * Color mode: ◆ (BRIGHT), ✓ (OK), ◇ (MUTED)
     * Plain mode: [>], [OK], [ ]
     */
    public static String indicator(Status status, boolean color) {
        if (color) {
            return switch (status) {
                case ACTIVE    -> AnsiPalette.BRIGHT + "◆" + AnsiPalette.RESET;
                case COMPLETED -> AnsiPalette.OK + "✓" + AnsiPalette.RESET;
                case PENDING   -> AnsiPalette.MUTED + "◇" + AnsiPalette.RESET;
            };
        } else {
            return switch (status) {
                case ACTIVE    -> "[>]";
                case COMPLETED -> "[OK]";
                case PENDING   -> "[ ]";
            };
        }
    }

    /**
     * Renders the two-row step index strip for 4 steps (NEXUS through CORPUS).
     * Layout:
     * <pre>
     *    I  NEXUS ........... ✓       III  ANIMA .......... ◆
     *   II  VOX ............. ✓        IV  CORPUS ......... ◇
     * </pre>
     *
     * @param steps exactly 4 step states
     * @param contentWidth available width for each row
     * @param color true for ANSI color output
     * @return two-element array of rendered lines
     */
    public static String[] renderStepIndex(List<StepState> steps, int contentWidth, boolean color) {
        if (steps.size() < 4) {
            throw new IllegalArgumentException("Need exactly 4 steps for index rendering");
        }

        String line1 = formatIndexLine(steps.get(0), steps.get(2), contentWidth, color);
        String line2 = formatIndexLine(steps.get(1), steps.get(3), contentWidth, color);
        return new String[]{line1, line2};
    }

    private static String formatIndexLine(StepState left, StepState right, int contentWidth, boolean color) {
        String leftCol = formatStepEntry(left, color);
        String rightCol = formatStepEntry(right, color);

        int halfWidth = contentWidth / 2;
        String leftPadded = padToVisible(leftCol, halfWidth, color);
        return leftPadded + rightCol;
    }

    private static String formatStepEntry(StepState step, boolean color) {
        String numeral = String.format("%4s", step.romanNumeral());
        String dots = dotLeader(step.latinName(), 14, color);
        String ind = indicator(step.status(), color);
        String nameColor = color ? AnsiPalette.TEXT : "";
        String reset = color ? AnsiPalette.RESET : "";
        return numeral + "  " + nameColor + step.latinName() + reset + " " + dots + " " + ind;
    }

    private static String dotLeader(String name, int totalWidth, boolean color) {
        int dotsNeeded = totalWidth - name.length();
        if (dotsNeeded < 1) dotsNeeded = 1;
        String dots = ".".repeat(dotsNeeded);
        if (color) {
            return AnsiPalette.MUTED + dots + AnsiPalette.RESET;
        }
        return dots;
    }

    private static String padToVisible(String text, int targetVisibleWidth, boolean color) {
        int visibleLen = TerminalCapabilities.strip(text).length();
        int padding = targetVisibleWidth - visibleLen;
        if (padding < 1) padding = 1;
        return text + " ".repeat(padding);
    }
}
