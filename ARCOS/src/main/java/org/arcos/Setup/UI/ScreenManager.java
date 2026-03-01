package org.arcos.Setup.UI;

import org.arcos.Setup.StepDefinition;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Full-screen alternate-buffer implementation of WizardDisplay.
 * Enters alternate screen, hides cursor, draws persistent chrome.
 * Thread-safe terminal writes via ReentrantLock (spinner thread + main thread).
 */
public class ScreenManager implements WizardDisplay {

    private static final String ENTER_ALT_SCREEN = "\033[?1049h";
    private static final String EXIT_ALT_SCREEN = "\033[?1049l";
    private static final String HIDE_CURSOR = "\033[?25l";
    private static final String SHOW_CURSOR = "\033[?25h";
    private static final String CLEAR_LINE = "\033[2K";

    private static final char[] SPINNER_FRAMES = {'◒', '◐', '◓', '◑'};
    private static final int SPINNER_INTERVAL_MS = 100;

    private final Terminal terminal;
    private final PrintWriter out;
    private final ReentrantLock writeLock = new ReentrantLock();

    private LayoutCalculator.ScreenLayout layout;
    private List<StepDefinition> stepDefs;
    private final List<StepIndicator.Status> stepStatuses = new ArrayList<>();
    private int activeStepIndex = -1;
    private int currentPanelRow = 0;

    private Thread shutdownHook;

    private volatile boolean altScreenActive = false;

    public ScreenManager(Terminal terminal) {
        this.terminal = terminal;
        this.out = terminal.writer();
        this.layout = LayoutCalculator.calculate(terminal.getWidth(), terminal.getHeight());

        // Shutdown hook to restore terminal on crash/Ctrl+C
        // Uses System.out directly as JLine's writer may be closed during shutdown
        shutdownHook = new Thread(() -> emergencyRestore(), "screen-cleanup");
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        enterAlternateScreen();
    }

    private void enterAlternateScreen() {
        writeLock.lock();
        try {
            out.print(ENTER_ALT_SCREEN);
            out.print(HIDE_CURSOR);
            out.print("\033[2J"); // clear screen
            out.flush();
            altScreenActive = true;
        } finally {
            writeLock.unlock();
        }
    }

    private void exitAlternateScreen() {
        writeLock.lock();
        try {
            if (altScreenActive) {
                out.print(SHOW_CURSOR);
                out.print(EXIT_ALT_SCREEN);
                out.flush();
                altScreenActive = false;
            }
        } finally {
            writeLock.unlock();
        }
    }

    /** Last-resort terminal restore via System.out, bypassing JLine. */
    private void emergencyRestore() {
        if (altScreenActive) {
            try {
                System.out.print(SHOW_CURSOR + EXIT_ALT_SCREEN);
                System.out.flush();
            } catch (Exception ignored) {}
            altScreenActive = false;
        }
    }

    @Override
    public void initializeSteps(List<StepDefinition> steps) {
        this.stepDefs = new ArrayList<>(steps);
        stepStatuses.clear();
        for (int i = 0; i < steps.size(); i++) {
            stepStatuses.add(StepIndicator.Status.PENDING);
        }
    }

    @Override
    public void drawFrame() {
        layout = LayoutCalculator.calculate(terminal.getWidth(), terminal.getHeight());
        writeLock.lock();
        try {
            // Header
            writeAt(layout.headerRow(), layout.leftMargin(), BoxDrawing.headerBar(layout.frameWidth(), false));

            // Empty row after header
            writeAt(layout.headerRow() + 1, layout.leftMargin(), BoxDrawing.emptyRow(layout.frameWidth(), false));

            // Step index
            drawStepIndex();

            // Empty row after step index
            writeAt(layout.stepIndexEnd() + 1, layout.leftMargin(), BoxDrawing.emptyRow(layout.frameWidth(), false));

            // Footer
            writeAt(layout.footerRow(), layout.leftMargin(), BoxDrawing.footer(layout.frameWidth(), false));

            // Empty rows for panel area
            for (int r = layout.panelDividerRow() + 1; r < layout.footerRow(); r++) {
                writeAt(r, layout.leftMargin(), BoxDrawing.emptyRow(layout.frameWidth(), false));
            }

            out.flush();
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void activateStep(int i) {
        activeStepIndex = i;
        if (i < stepStatuses.size()) {
            stepStatuses.set(i, StepIndicator.Status.ACTIVE);
        }
        writeLock.lock();
        try {
            drawStepIndex();
            drawPanelDivider();
            clearPanel();
            out.flush();
        } finally {
            writeLock.unlock();
        }
        currentPanelRow = 0;
    }

    @Override
    public void completeStep(int i) {
        if (i < stepStatuses.size()) {
            stepStatuses.set(i, StepIndicator.Status.COMPLETED);
        }
        writeLock.lock();
        try {
            drawStepIndex();
            out.flush();
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void printLine(int row, String text) {
        int absRow = layout.panelContentStart() + row;
        if (absRow > layout.panelContentEnd()) return; // overflow protection
        writeLock.lock();
        try {
            writeAt(absRow, layout.leftMargin(),
                    BoxDrawing.contentRow(text, layout.frameWidth(), false));
            out.flush();
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void printLine(String text) {
        printLine(currentPanelRow, text);
        currentPanelRow++;
    }

    @Override
    public void clearPanel() {
        writeLock.lock();
        try {
            for (int r = layout.panelContentStart(); r <= layout.panelContentEnd(); r++) {
                writeAt(r, layout.leftMargin(), BoxDrawing.emptyRow(layout.frameWidth(), false));
            }
            out.flush();
        } finally {
            writeLock.unlock();
        }
        currentPanelRow = 0;
    }

    @Override
    public void statusLine(String label, String value, String detail, StatusColor statusColor) {
        String line = StatusLineRenderer.render(label, value, detail, statusColor,
                layout.contentWidth(), true);
        printLine(line);
    }

    @Override
    public void gauge(String label, int value, int labelWidth) {
        printLine(GaugeRenderer.render(label, value, labelWidth, true));
    }

    @Override
    public String gaugeCompact(String abbreviation, int value) {
        return GaugeRenderer.renderCompact(abbreviation, value, true);
    }

    @Override
    public String readLine(String prompt) {
        int absRow = layout.panelContentStart() + currentPanelRow;
        positionCursorForInput(absRow, prompt);
        try {
            LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();
            String input = reader.readLine(prompt);
            writeLock.lock();
            try {
                out.print(HIDE_CURSOR);
                out.flush();
            } finally {
                writeLock.unlock();
            }
            currentPanelRow++;
            return input != null ? input.trim() : null;
        } catch (Exception e) {
            writeLock.lock();
            try {
                out.print(HIDE_CURSOR);
                out.flush();
            } finally {
                writeLock.unlock();
            }
            return null;
        }
    }

    @Override
    public String readMaskedLine(String prompt) {
        int absRow = layout.panelContentStart() + currentPanelRow;
        positionCursorForInput(absRow, prompt);
        try {
            LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();
            String input = reader.readLine(prompt, '*');
            writeLock.lock();
            try {
                out.print(HIDE_CURSOR);
                out.flush();
            } finally {
                writeLock.unlock();
            }
            currentPanelRow++;
            return input != null ? input.trim() : null;
        } catch (Exception e) {
            writeLock.lock();
            try {
                out.print(HIDE_CURSOR);
                out.flush();
            } finally {
                writeLock.unlock();
            }
            return null;
        }
    }

    @Override
    public SpinnerHandle showSpinner(String label) {
        int row = currentPanelRow;
        int absRow = layout.panelContentStart() + row;

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "spinner");
            t.setDaemon(true);
            return t;
        });

        final int[] frameCounter = {0};
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            char spinChar = SPINNER_FRAMES[frameCounter[0] % SPINNER_FRAMES.length];
            frameCounter[0]++;
            writeLock.lock();
            try {
                String spinText = AnsiPalette.PRIMARY + spinChar + AnsiPalette.RESET + "  " + label;
                writeAt(absRow, layout.leftMargin(),
                        BoxDrawing.contentRow(spinText, layout.frameWidth(), false));
                out.flush();
            } finally {
                writeLock.unlock();
            }
        }, 0, SPINNER_INTERVAL_MS, TimeUnit.MILLISECONDS);

        return resultText -> {
            task.cancel(false);
            scheduler.shutdownNow();
            writeLock.lock();
            try {
                writeAt(absRow, layout.leftMargin(),
                        BoxDrawing.contentRow(resultText, layout.frameWidth(), false));
                out.flush();
            } finally {
                writeLock.unlock();
            }
        };
    }

    @Override
    public void showError(String message) {
        printLine(AnsiPalette.BRIGHT + "✗" + AnsiPalette.RESET + " " + message);
    }

    @Override
    public void waitForKey() {
        readLine(AnsiPalette.MUTED + "[Enter]" + AnsiPalette.RESET + " ");
    }

    @Override
    public int getContentWidth() {
        return layout.contentWidth();
    }

    @Override
    public boolean isColorSupported() {
        return true; // ScreenManager is only used when color is supported
    }

    @Override
    public void close() {
        exitAlternateScreen();
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // JVM already shutting down
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private void drawStepIndex() {
        // Build step states for the first 4 steps (NEXUS through CORPUS)
        List<StepState> states = new ArrayList<>();
        int count = Math.min(4, stepDefs.size());
        for (int i = 0; i < count; i++) {
            StepDefinition def = stepDefs.get(i);
            StepIndicator.Status status = i < stepStatuses.size()
                    ? stepStatuses.get(i)
                    : StepIndicator.Status.PENDING;
            states.add(new StepState(def.romanNumeral(), def.latinName(), status));
        }
        // Pad to 4 if needed
        while (states.size() < 4) {
            states.add(new StepState("", "---", StepIndicator.Status.PENDING));
        }

        String[] indexLines = StepIndicator.renderStepIndex(states, layout.contentWidth(), true);
        writeAt(layout.stepIndexStart(), layout.leftMargin(),
                BoxDrawing.contentRow(indexLines[0], layout.frameWidth(), false));
        writeAt(layout.stepIndexEnd(), layout.leftMargin(),
                BoxDrawing.contentRow(indexLines[1], layout.frameWidth(), false));
    }

    private void drawPanelDivider() {
        String numeral = "";
        String name = "FIAT";
        if (activeStepIndex >= 0 && activeStepIndex < stepDefs.size()) {
            StepDefinition def = stepDefs.get(activeStepIndex);
            numeral = def.romanNumeral();
            name = def.latinName();
        }
        writeAt(layout.panelDividerRow(), layout.leftMargin(),
                BoxDrawing.panelDivider(numeral, name, layout.frameWidth(), false));
    }

    private void positionCursorForInput(int row, String prompt) {
        writeLock.lock();
        try {
            // Clear the line and position cursor
            int col = layout.leftMargin() + 4; // inside border + margin
            out.print("\033[" + row + ";" + col + "H" + CLEAR_LINE);
            out.print(SHOW_CURSOR);
            out.print("\033[" + row + ";" + col + "H");
            out.flush();
        } finally {
            writeLock.unlock();
        }
    }

    private void writeAt(int row, int colOffset, String text) {
        // Position cursor at row, col (1-based)
        int col = colOffset + 1;
        out.print("\033[" + row + ";" + col + "H" + CLEAR_LINE + text);
    }
}
