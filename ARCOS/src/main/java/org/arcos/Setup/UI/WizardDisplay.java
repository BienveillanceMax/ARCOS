package org.arcos.Setup.UI;

import org.arcos.Setup.StepDefinition;

import java.util.List;

/**
 * Display abstraction for the wizard TUI.
 * Two implementations: ScreenManager (full-screen) and FallbackRenderer (scrolling).
 * Steps interact only through this interface — never with raw Terminal/PrintWriter.
 */
public interface WizardDisplay {

    /** Initialize step index with step definitions. */
    void initializeSteps(List<StepDefinition> steps);

    /** Draw the full frame (header, step index, borders). */
    void drawFrame();

    /** Mark step at index i as active, redraw step index and panel divider. */
    void activateStep(int i);

    /** Mark step at index i as completed, redraw step index. */
    void completeStep(int i);

    /** Print text at an explicit panel row (0-based relative to panel content area). */
    void printLine(int row, String text);

    /** Print text at the next auto-increment row. */
    void printLine(String text);

    /** Clear the panel content area. */
    void clearPanel();

    /**
     * Render a dot-leader status line at the current row.
     */
    void statusLine(String label, String value, String detail, StatusColor statusColor);

    /**
     * Render a full-width gauge at the current row.
     */
    void gauge(String label, int value, int labelWidth);

    /**
     * Render a compact gauge (returns the string, does not print).
     */
    String gaugeCompact(String abbreviation, int value);

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
