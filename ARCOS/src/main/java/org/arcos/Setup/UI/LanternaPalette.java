package org.arcos.Setup.UI;

import com.googlecode.lanterna.TextColor;

/**
 * ANSI 256-color palette for Lanterna rendering.
 * Same color indices as AnsiPalette but as TextColor objects for TextGraphics.
 */
public record LanternaPalette(
        TextColor bright,
        TextColor primary,
        TextColor deep,
        TextColor text,
        TextColor muted,
        TextColor dim,
        TextColor ok,
        TextColor warn,
        TextColor info
) {
    /** Default NERV-inspired red identity palette. */
    public static final LanternaPalette DEFAULT = new LanternaPalette(
            new TextColor.Indexed(196), // #FF0000 — active header, emphasis, errors
            new TextColor.Indexed(160), // #D70000 — borders, gauges, indicators
            new TextColor.Indexed(52),  // #5F0000 — inactive borders, structural depth
            new TextColor.Indexed(252), // #D0D0D0 — all readable content
            new TextColor.Indexed(243), // #767676 — dot leaders, secondary
            new TextColor.Indexed(237), // #3A3A3A — faintest separators
            new TextColor.Indexed(71),  // #5FAF5F — online, validated
            new TextColor.Indexed(178), // #D7AF00 — degraded, partial
            new TextColor.Indexed(67)   // #5F87AF — secondary readouts
    );

    /** Maps StatusColor enum to the corresponding TextColor. */
    public TextColor forStatus(StatusColor statusColor) {
        return switch (statusColor) {
            case OK -> ok;
            case WARN -> warn;
            case BRIGHT -> bright;
            case INFO -> info;
            case MUTED -> muted;
        };
    }
}
