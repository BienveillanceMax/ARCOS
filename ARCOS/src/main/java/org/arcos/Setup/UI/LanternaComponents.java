package org.arcos.Setup.UI;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;

/**
 * Static drawing methods for Lanterna TextGraphics.
 * All methods draw directly — caller is responsible for screen.refresh() and locking.
 */
public final class LanternaComponents {

    private LanternaComponents() {}

    private static final int DOT_LEADER_WIDTH = 14;
    private static final String SYS_FINGERPRINT = String.format("%04X",
            (System.getProperty("user.name", "arcos") + ProcessHandle.current().pid()).hashCode() & 0xFFFF);

    /**
     * Draws the header bar: ┏━━ ARCOS ━━━━━━━━━━━━━━━━ INITIUM v1.0 ━━━━┓
     */
    public static void drawHeaderBar(TextGraphics tg, LayoutCalculator.ScreenLayout layout,
                                     LanternaPalette palette) {
        int row = layout.headerRow();
        int x = layout.leftMargin();
        int w = layout.frameWidth();

        String leftLabel = " ARCOS ";
        String rightLabel = " INITIUM v1.0 ";
        int fillLeft = 2;
        int fillRight = 4;
        int fillMid = w - 2 - fillLeft - leftLabel.length() - fillRight - rightLabel.length();
        if (fillMid < 0) fillMid = 0;

        tg.setForegroundColor(palette.primary());
        tg.putString(x, row, "┏" + "━".repeat(fillLeft));

        tg.setForegroundColor(palette.bright());
        tg.putString(x + 1 + fillLeft, row, leftLabel);

        tg.setForegroundColor(palette.primary());
        tg.putString(x + 1 + fillLeft + leftLabel.length(), row, "━".repeat(fillMid));

        tg.setForegroundColor(palette.muted());
        tg.putString(x + 1 + fillLeft + leftLabel.length() + fillMid, row, rightLabel);

        tg.setForegroundColor(palette.primary());
        tg.putString(x + w - 1 - fillRight, row, "━".repeat(fillRight) + "┓");
    }

    /**
     * Draws the footer bar: ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
     */
    public static void drawFooter(TextGraphics tg, LayoutCalculator.ScreenLayout layout,
                                  LanternaPalette palette) {
        int row = layout.footerRow();
        int x = layout.leftMargin();
        int w = layout.frameWidth();
        String label = " SYS:" + SYS_FINGERPRINT + " ";
        int fillBefore = w - label.length() - 4;
        if (fillBefore < 1) fillBefore = 1;

        tg.setForegroundColor(palette.primary());
        tg.putString(x, row, "┗" + "━".repeat(fillBefore));
        tg.setForegroundColor(palette.dim());
        tg.putString(x + 1 + fillBefore, row, label);
        tg.setForegroundColor(palette.primary());
        tg.putString(x + 1 + fillBefore + label.length(), row, "━━┛");
    }

    /**
     * Draws a panel divider: ┣━ III // ANIMA ━━━━━━━━━━━━━━━━━━━━━━━━━━┫
     */
    public static void drawPanelDivider(TextGraphics tg, LayoutCalculator.ScreenLayout layout,
                                        String numeral, String name, LanternaPalette palette) {
        int row = layout.panelDividerRow();
        int x = layout.leftMargin();
        int w = layout.frameWidth();

        String label;
        if (numeral == null || numeral.isEmpty()) {
            label = " " + name + " ";
        } else {
            label = " " + numeral + " // " + name + " ";
        }

        int fillLen = w - 2 - label.length();
        if (fillLen < 0) fillLen = 0;

        tg.setForegroundColor(palette.primary());
        tg.putString(x, row, "┣━");

        tg.setForegroundColor(palette.bright());
        tg.putString(x + 2, row, label);

        tg.setForegroundColor(palette.primary());
        tg.putString(x + 2 + label.length(), row, "━".repeat(Math.max(0, fillLen - 1)) + "┫");
    }

    /**
     * Draws an empty row with side borders.
     */
    public static void drawEmptyRow(TextGraphics tg, LayoutCalculator.ScreenLayout layout,
                                    int row, LanternaPalette palette) {
        int x = layout.leftMargin();
        int w = layout.frameWidth();
        tg.setForegroundColor(palette.primary());
        tg.putString(x, row, "┃");
        // Clear interior with spaces
        tg.setForegroundColor(palette.text());
        tg.putString(x + 1, row, " ".repeat(w - 2));
        tg.setForegroundColor(palette.primary());
        tg.putString(x + w - 1, row, "┃");
    }

    /**
     * Draws a content row with text inside borders, left-aligned with 3-char margin.
     */
    public static void drawContentRow(TextGraphics tg, LayoutCalculator.ScreenLayout layout,
                                      int row, String text, TextColor color,
                                      LanternaPalette palette) {
        int x = layout.leftMargin();
        int w = layout.frameWidth();
        int maxTextLen = w - 6; // borders + margins

        // Left border
        tg.setForegroundColor(palette.primary());
        tg.putString(x, row, "┃");

        // Content (left margin of 3 spaces)
        String displayText = text.length() > maxTextLen ? text.substring(0, maxTextLen) : text;
        int padding = w - 2 - 3 - displayText.length();
        if (padding < 1) padding = 1;

        tg.setForegroundColor(color);
        tg.putString(x + 1, row, "   " + displayText + " ".repeat(padding));

        // Right border
        tg.setForegroundColor(palette.primary());
        tg.putString(x + w - 1, row, "┃");
    }

    /**
     * Draws a content row using default text color.
     */
    public static void drawContentRow(TextGraphics tg, LayoutCalculator.ScreenLayout layout,
                                      int row, String text, LanternaPalette palette) {
        drawContentRow(tg, layout, row, text, palette.text(), palette);
    }

    /**
     * Draws a dot-leader status line at the given row.
     */
    public static void drawStatusLine(TextGraphics tg, LayoutCalculator.ScreenLayout layout,
                                      int row, String label, String value, String detail,
                                      TextColor statusColor, LanternaPalette palette) {
        int x = layout.leftMargin();
        int w = layout.frameWidth();
        int contentW = layout.contentWidth();

        // Calculate dot leaders — truncate value if it would exceed content width
        String detailSuffix = (detail != null && !detail.isEmpty()) ? "  " + detail : "";
        int minDots = 3;
        int overhead = label.length() + detailSuffix.length() + minDots + 2; // 2 = spaces around dots
        int maxValueLen = contentW - overhead;
        String displayValue = value;
        if (maxValueLen < 4) {
            // Not enough room even for a truncated value — show just the value, no dots
            maxValueLen = contentW - label.length() - 2;
            displayValue = truncateWithEllipsis(value, Math.max(1, maxValueLen));
            minDots = 0;
        } else if (value.length() > maxValueLen) {
            displayValue = truncateWithEllipsis(value, maxValueLen);
        }
        int valueLen = displayValue.length() + detailSuffix.length();
        int dotsNeeded = contentW - label.length() - valueLen - 2;
        if (dotsNeeded < minDots) dotsNeeded = minDots;
        String dots = dotsNeeded > 0 ? ".".repeat(dotsNeeded) : "";

        // Left border
        tg.setForegroundColor(palette.primary());
        tg.putString(x, row, "┃");

        int cx = x + 4; // inside border + 3-char margin

        // Label
        tg.setForegroundColor(palette.text());
        tg.putString(cx, row, label);
        cx += label.length();

        // Dots
        if (dotsNeeded > 0) {
            tg.putString(cx, row, " ");
            cx++;
            tg.setForegroundColor(palette.muted());
            tg.putString(cx, row, dots);
            cx += dots.length();
            tg.putString(cx, row, " ");
            cx++;
        } else {
            tg.putString(cx, row, " ");
            cx++;
        }

        // Value
        tg.setForegroundColor(statusColor);
        tg.putString(cx, row, displayValue);
        cx += displayValue.length();

        // Detail
        if (detail != null && !detail.isEmpty()) {
            tg.setForegroundColor(palette.muted());
            tg.putString(cx, row, "  " + detail);
            cx += 2 + detail.length();
        }

        // Pad to right border
        int remaining = x + w - 1 - cx;
        if (remaining > 0) {
            tg.putString(cx, row, " ".repeat(remaining));
        }

        // Right border
        tg.setForegroundColor(palette.primary());
        tg.putString(x + w - 1, row, "┃");
    }

    /**
     * Draws a full-width gauge at the given row inside the frame.
     */
    public static void drawGauge(TextGraphics tg, LayoutCalculator.ScreenLayout layout,
                                 int row, String label, int value, int labelWidth,
                                 LanternaPalette palette) {
        int x = layout.leftMargin();
        int w = layout.frameWidth();
        int clamped = Math.max(0, Math.min(100, value));
        int filled = Math.round(clamped / 10.0f);
        int empty = 10 - filled;

        // Left border
        tg.setForegroundColor(palette.primary());
        tg.putString(x, row, "┃");

        int cx = x + 4;

        // Padded label
        String paddedLabel = label.length() >= labelWidth ? label : label + " ".repeat(labelWidth - label.length());
        tg.setForegroundColor(palette.text());
        tg.putString(cx, row, paddedLabel);
        cx += paddedLabel.length() + 2;

        // Gauge fill
        tg.setForegroundColor(palette.primary());
        tg.putString(cx, row, "━".repeat(filled));
        cx += filled;
        tg.setForegroundColor(palette.muted());
        tg.putString(cx, row, "─".repeat(empty));
        cx += empty + 2;

        // Value
        String valueStr = String.format("%3d", clamped);
        tg.setForegroundColor(palette.muted());
        tg.putString(cx, row, valueStr);
        cx += valueStr.length();

        // Pad to right border
        int remaining = x + w - 1 - cx;
        if (remaining > 0) {
            tg.putString(cx, row, " ".repeat(remaining));
        }

        // Right border
        tg.setForegroundColor(palette.primary());
        tg.putString(x + w - 1, row, "┃");
    }

    /**
     * Renders a compact gauge string (not drawn to screen, returns for embedding).
     */
    public static String gaugeCompactText(String abbreviation, int value) {
        int clamped = Math.max(0, Math.min(100, value));
        int filled = Math.round(clamped / 10.0f);
        int empty = 10 - filled;
        String abbr = abbreviation.length() >= 3 ? abbreviation : abbreviation + " ".repeat(3 - abbreviation.length());
        return abbr + " " + "━".repeat(filled) + "─".repeat(empty) + " " + String.format("%3d", clamped);
    }

    /**
     * Draws the step index strip (3 rows: 2×2 grid + optional centered FIAT row)
     * with native Lanterna TextColor rendering per segment.
     */
    public static void drawStepIndex(TextGraphics tg, LayoutCalculator.ScreenLayout layout,
                                     java.util.List<StepState> states, LanternaPalette palette) {
        int halfWidth = layout.contentWidth() / 2;
        int cx = layout.leftMargin() + 4;

        int row1 = layout.stepIndexStart();
        drawEmptyRow(tg, layout, row1, palette);
        drawNativeStepEntry(tg, cx, row1, states.get(0), palette);
        if (states.size() > 2) {
            drawNativeStepEntry(tg, cx + halfWidth, row1, states.get(2), palette);
        }

        int row2 = layout.stepIndexStart() + 1;
        drawEmptyRow(tg, layout, row2, palette);
        if (states.size() > 1) {
            drawNativeStepEntry(tg, cx, row2, states.get(1), palette);
        }
        if (states.size() > 3) {
            drawNativeStepEntry(tg, cx + halfWidth, row2, states.get(3), palette);
        }

        int row3 = layout.stepIndexStart() + 2;
        if (states.size() >= 5 && !states.get(4).latinName().isEmpty()) {
            drawEmptyRow(tg, layout, row3, palette);
            int entryWidth = stepEntryWidth(states.get(4));
            int centerX = cx + (layout.contentWidth() - entryWidth) / 2;
            drawNativeStepEntry(tg, centerX, row3, states.get(4), palette);
        } else {
            drawEmptyRow(tg, layout, row3, palette);
        }
    }

    private static int stepEntryWidth(StepState step) {
        int dots = Math.max(1, DOT_LEADER_WIDTH - step.latinName().length());
        return 4 + 2 + step.latinName().length() + 1 + dots + 1 + 1;
    }

    private static void drawNativeStepEntry(TextGraphics tg, int x, int y,
                                             StepState step, LanternaPalette palette) {
        String numeral = String.format("%4s", step.romanNumeral());
        tg.setForegroundColor(palette.muted());
        tg.putString(x, y, numeral);
        x += 4;

        tg.putString(x, y, "  ");
        x += 2;

        tg.setForegroundColor(palette.text());
        tg.putString(x, y, step.latinName());
        x += step.latinName().length();

        int dotsNeeded = Math.max(1, DOT_LEADER_WIDTH - step.latinName().length());
        tg.putString(x, y, " ");
        x += 1;
        tg.setForegroundColor(palette.muted());
        tg.putString(x, y, ".".repeat(dotsNeeded));
        x += dotsNeeded;
        tg.putString(x, y, " ");
        x += 1;

        TextColor color = switch (step.status()) {
            case ACTIVE -> palette.bright();
            case COMPLETED -> palette.ok();
            case PENDING -> palette.muted();
        };
        String symbol = switch (step.status()) {
            case ACTIVE -> "◆";
            case COMPLETED -> "✓";
            case PENDING -> "◇";
        };
        tg.setForegroundColor(color);
        tg.putString(x, y, symbol);
    }

    /**
     * Draws all frame borders (left/right ┃) for rows between header and footer.
     */
    public static void drawBorders(TextGraphics tg, LayoutCalculator.ScreenLayout layout,
                                   LanternaPalette palette) {
        drawHeaderBar(tg, layout, palette);
        drawFooter(tg, layout, palette);
        for (int r = layout.headerRow() + 1; r < layout.footerRow(); r++) {
            drawEmptyRow(tg, layout, r, palette);
        }
    }

    /**
     * Truncates a string to maxLen, replacing the end with "..." if needed.
     * For file paths, tries to preserve the filename by trimming the middle.
     */
    private static String truncateWithEllipsis(String text, int maxLen) {
        if (text.length() <= maxLen) return text;
        if (maxLen <= 3) return text.substring(0, maxLen);

        // For paths: keep start and end, ellipsis in the middle
        int keep = (maxLen - 3) / 2;
        return text.substring(0, keep) + "..." + text.substring(text.length() - (maxLen - 3 - keep));
    }
}
