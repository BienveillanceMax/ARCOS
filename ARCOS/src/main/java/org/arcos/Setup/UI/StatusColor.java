package org.arcos.Setup.UI;

/**
 * Semantic status colors for wizard display elements.
 * Maps to AnsiPalette constants.
 */
public enum StatusColor {

    OK(AnsiPalette.OK),
    WARN(AnsiPalette.WARN),
    BRIGHT(AnsiPalette.BRIGHT),
    INFO(AnsiPalette.INFO),
    MUTED(AnsiPalette.MUTED);

    private final String ansiCode;

    StatusColor(String ansiCode) {
        this.ansiCode = ansiCode;
    }

    public String ansiCode() { return ansiCode; }
}
