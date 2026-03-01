package org.arcos.Setup.UI;

/**
 * ANSI 256-color palette for ARCOS terminal displays.
 * Red identity palette — NERV-inspired institutional aesthetic.
 *
 * <h3>Accessibility Enforcement (vs #000000 black background)</h3>
 * <ul>
 *   <li><b>PRIMARY</b>: borders and gauge fill ONLY — never text labels (3.89:1, fails AA text)</li>
 *   <li><b>DEEP/DIM</b>: structural/decorative elements ONLY — never carries meaning alone</li>
 *   <li><b>All readable text</b>: must use TEXT, MUTED, or status colors (OK/WARN/BRIGHT/INFO)</li>
 * </ul>
 */
public final class AnsiPalette {

    private AnsiPalette() {}

    public static final String RESET = "\033[0m";
    public static final String BOLD  = "\033[1m";

    // ── Red identity palette ────────────────────────────────────────────────
    public static final String BRIGHT  = "\033[38;5;196m"; // #FF0000 — active header, emphasis, errors
    public static final String PRIMARY = "\033[38;5;160m"; // #D70000 — borders, gauges, indicators
    public static final String DEEP    = "\033[38;5;52m";  // #5F0000 — inactive borders, structural depth

    // ── Text ────────────────────────────────────────────────────────────────
    public static final String TEXT  = "\033[38;5;252m"; // #D0D0D0 — all readable content
    public static final String MUTED = "\033[38;5;243m"; // #767676 — dot leaders, secondary
    public static final String DIM   = "\033[38;5;237m"; // #3A3A3A — faintest separators

    // ── Status ──────────────────────────────────────────────────────────────
    public static final String OK   = "\033[38;5;71m";  // #5FAF5F — online, validated
    public static final String WARN = "\033[38;5;178m"; // #D7AF00 — degraded, partial
    public static final String INFO = "\033[38;5;67m";  // #5F87AF — secondary readouts

    // ── Deprecated aliases (keep BootReportRenderer / AsciiBanner compiling) ─
    /** @deprecated Use {@link #PRIMARY} */
    @Deprecated public static final String ORANGE_BRIGHT = PRIMARY;
    /** @deprecated Use {@link #BRIGHT} */
    @Deprecated public static final String AMBER = BRIGHT;
    /** @deprecated Use {@link #DEEP} */
    @Deprecated public static final String ORANGE_DARK = DEEP;
    /** @deprecated Use {@link #OK} */
    @Deprecated public static final String GREEN = OK;
    /** @deprecated Use {@link #BRIGHT} */
    @Deprecated public static final String RED = BRIGHT;
    /** @deprecated Use {@link #WARN} */
    @Deprecated public static final String YELLOW = WARN;
    /** @deprecated Use {@link #INFO} */
    @Deprecated public static final String CYAN = INFO;
    /** @deprecated Use {@link #MUTED} */
    @Deprecated public static final String GRAY_LIGHT = MUTED;
    /** @deprecated Use {@link #TEXT} */
    @Deprecated public static final String WHITE = TEXT;
}
