package org.arcos.Setup.UI;

import org.arcos.Setup.StepDefinition;

import java.util.List;

/**
 * Display abstraction for the wizard TUI.
 * Two implementations: LanternaScreenManager (full-screen) and FallbackRenderer (scrolling).
 * Steps interact only through this interface — never with raw Terminal/PrintWriter.
 */
public interface WizardDisplay {

    /** Sentinel returned by selectMenu when the user backs out (Esc / 'b'). */
    int MENU_BACK = -2;

    /**
     * One selectable entry in an interactive menu.
     *
     * @param label      primary text (ALL CAPS system label or device name)
     * @param annotation secondary text drawn after the label (muted); may be null
     */
    record MenuItem(String label, String annotation) {
        public static MenuItem of(String label) {
            return new MenuItem(label, null);
        }
    }

    /** Initialize step index with step definitions. */
    void initializeSteps(List<StepDefinition> steps);

    /** Draw the full frame (header, step index, borders). */
    void drawFrame();

    /** Mark step at index i as active, redraw step index and panel divider. */
    void activateStep(int i);

    /** Mark step at index i as completed, redraw step index. */
    void completeStep(int i);

    /** Mark step at index i back to pending (backwards navigation). */
    default void resetStep(int i) {}

    /** Print text at an explicit panel row (0-based relative to panel content area). */
    void printLine(int row, String text);

    /** Print text at the next auto-increment row. */
    void printLine(String text);

    /** Clear the panel content area. */
    void clearPanel();

    /**
     * Move the auto-increment cursor to an explicit panel row.
     * Enables fixed retry regions that rewrite in place instead of creeping down.
     * No-op in fallback mode (sequential output).
     */
    default void setRow(int row) {}

    /**
     * Render a dot-leader status line at the current row.
     */
    void statusLine(String label, String value, String detail, StatusColor statusColor);

    /**
     * Render a full-width gauge at the current row.
     */
    void gauge(String label, int value, int labelWidth);

    /**
     * Render a full-width gauge at an explicit panel row (for live detail panes).
     */
    void gauge(int row, String label, int value, int labelWidth);

    /**
     * Render a compact gauge (returns the string, does not print).
     */
    String gaugeCompact(String abbreviation, int value);

    /**
     * Interactive selection menu at the current row.
     * Full-screen mode: arrow-key navigation with a highlighted selection bar;
     * digits act as accelerators; Esc returns {@link #MENU_BACK}.
     * Fallback mode: numbered list + line input ('b' returns {@link #MENU_BACK}).
     *
     * @param items        menu entries (1-9 supported)
     * @param defaultIndex initially highlighted entry (fallback: chosen on empty input)
     * @return selected index, or {@link #MENU_BACK}
     */
    default int selectMenu(List<MenuItem> items, int defaultIndex) {
        return selectMenu(items, defaultIndex, null);
    }

    /**
     * Interactive selection menu with a live-highlight callback.
     * The callback fires once for the initial highlight and on every navigation
     * change — steps use it to redraw a detail pane below the menu.
     * Fallback mode ignores the callback (no live navigation).
     */
    int selectMenu(List<MenuItem> items, int defaultIndex, java.util.function.IntConsumer onHighlight);

    /**
     * Dramatic single-line reveal at the current row (scramble-decode in
     * full-screen mode, plain print in fallback). Reserved for earned moments:
     * FACTUM EST., CORPUS INTEGRUM. Defaults to BRIGHT.
     */
    default void reveal(String text) {
        reveal(text, StatusColor.BRIGHT);
    }

    /**
     * Dramatic single-line reveal resolving into the given status color
     * (OK for CORPUS INTEGRUM — success is sage, only red is vivid).
     */
    void reveal(String text, StatusColor color);

    /**
     * Light horizontal rule with an inline label at an explicit panel row:
     * ── LABEL ───────────. Internal structure against the heavy frame.
     */
    void rule(int row, String label);

    /**
     * Set the contextual key hints shown in the footer bar
     * (e.g. "↑↓ NAV · ↵ SELECT · ESC BACK"). No-op in fallback mode.
     */
    void setKeyHints(String hints);

    /** Read a line of text input from the user. */
    String readLine(String prompt);

    /** Read a masked (password) line of input. */
    String readMaskedLine(String prompt);

    /** Show a spinner with the given label. Returns a handle to stop it. */
    SpinnerHandle showSpinner(String label);

    /** Show an error message. */
    void showError(String message);

    /** Wait for user to press Enter. */
    void waitForKey();

    /** Get the usable content width inside the panel. */
    int getContentWidth();

    /** Whether color output is supported. */
    boolean isColorSupported();

    /** Clean up resources (exit alternate screen, show cursor). */
    void close();

    /**
     * Handle for stopping a spinner.
     */
    interface SpinnerHandle {
        /** Stop the spinner and display the result text. */
        void stop(String resultText);
    }
}
