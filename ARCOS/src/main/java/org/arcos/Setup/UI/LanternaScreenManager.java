package org.arcos.Setup.UI;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import org.arcos.Setup.StepDefinition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Full-screen Lanterna implementation of WizardDisplay.
 * Double-buffered via Screen layer — no flicker.
 * Receives an already-started Screen from the unified boot flow.
 */
public class LanternaScreenManager implements WizardDisplay {

    private static final char[] SPINNER_FRAMES = Animations.SPINNER_FRAMES;

    private final Screen screen;
    private final TextGraphics tg;
    private final ReentrantLock writeLock = new ReentrantLock();
    private final LanternaPalette palette = LanternaPalette.DEFAULT;

    private LayoutCalculator.ScreenLayout layout;
    private List<StepDefinition> stepDefs;
    private final List<StepIndicator.Status> stepStatuses = new ArrayList<>();
    private int activeStepIndex = -1;
    private int currentPanelRow = 0;

    public LanternaScreenManager(Screen screen) {
        this.screen = screen;
        this.tg = screen.newTextGraphics();
        TerminalSize size = screen.getTerminalSize();
        this.layout = LayoutCalculator.calculate(size);
        screen.setCursorPosition(null); // hide cursor
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
        recalculateLayout();
        writeLock.lock();
        try {
            LanternaComponents.drawBorders(tg, layout, palette);
            drawStepIndex();
            refresh();
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
            clearPanelInternal();
            refresh();
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
            refresh();
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void printLine(int row, String text) {
        int absRow = layout.panelContentStart() + row;
        if (absRow > layout.panelContentEnd()) return;
        String clean = stripAnsi(text);
        writeLock.lock();
        try {
            LanternaComponents.drawContentRow(tg, layout, absRow, clean, palette);
            refresh();
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
            clearPanelInternal();
            refresh();
        } finally {
            writeLock.unlock();
        }
        currentPanelRow = 0;
    }

    @Override
    public void statusLine(String label, String value, String detail, StatusColor statusColor) {
        int absRow = layout.panelContentStart() + currentPanelRow;
        if (absRow > layout.panelContentEnd()) return;
        writeLock.lock();
        try {
            TextColor color = palette.forStatus(statusColor);
            LanternaComponents.drawStatusLine(tg, layout, absRow, label, value, detail, color, palette);
            refresh();
        } finally {
            writeLock.unlock();
        }
        currentPanelRow++;
    }

    @Override
    public void gauge(String label, int value, int labelWidth) {
        int absRow = layout.panelContentStart() + currentPanelRow;
        if (absRow > layout.panelContentEnd()) return;
        writeLock.lock();
        try {
            LanternaComponents.drawGauge(tg, layout, absRow, label, value, labelWidth, palette);
            refresh();
        } finally {
            writeLock.unlock();
        }
        currentPanelRow++;
    }

    @Override
    public String gaugeCompact(String abbreviation, int value) {
        return LanternaComponents.gaugeCompactText(abbreviation, value);
    }

    @Override
    public String readLine(String prompt) {
        int absRow = layout.panelContentStart() + currentPanelRow;
        int inputX = layout.leftMargin() + 4;
        String cleanPrompt = stripAnsi(prompt);

        writeLock.lock();
        try {
            tg.setForegroundColor(palette.text());
            tg.putString(inputX, absRow, cleanPrompt);
            screen.setCursorPosition(new TerminalPosition(inputX + cleanPrompt.length(), absRow));
            refresh();
        } finally {
            writeLock.unlock();
        }

        String result = readInputLoop(inputX + cleanPrompt.length(), absRow, false);
        screen.setCursorPosition(null);
        currentPanelRow++;
        return result;
    }

    @Override
    public String readMaskedLine(String prompt) {
        int absRow = layout.panelContentStart() + currentPanelRow;
        int inputX = layout.leftMargin() + 4;
        String cleanPrompt = stripAnsi(prompt);

        writeLock.lock();
        try {
            tg.setForegroundColor(palette.text());
            tg.putString(inputX, absRow, cleanPrompt);
            screen.setCursorPosition(new TerminalPosition(inputX + cleanPrompt.length(), absRow));
            refresh();
        } finally {
            writeLock.unlock();
        }

        String result = readInputLoop(inputX + cleanPrompt.length(), absRow, true);
        screen.setCursorPosition(null);
        currentPanelRow++;
        return result;
    }

    @Override
    public SpinnerHandle showSpinner(String label) {
        int row = currentPanelRow;
        currentPanelRow++;
        int absRow = layout.panelContentStart() + row;

        Thread spinThread = Thread.ofVirtual().name("wizard-spinner").start(() -> {
            int frame = 0;
            while (!Thread.currentThread().isInterrupted()) {
                char spinChar = SPINNER_FRAMES[frame % SPINNER_FRAMES.length];
                frame++;
                writeLock.lock();
                try {
                    int cx = layout.leftMargin() + 4;
                    tg.setForegroundColor(palette.primary());
                    tg.putString(cx, absRow, String.valueOf(spinChar));
                    tg.setForegroundColor(palette.text());
                    tg.putString(cx + 2, absRow, label);
                    // Pad remainder
                    int remaining = layout.leftMargin() + layout.frameWidth() - 1 - (cx + 2 + label.length());
                    if (remaining > 0) {
                        tg.putString(cx + 2 + label.length(), absRow, " ".repeat(remaining));
                    }
                    tg.setForegroundColor(palette.primary());
                    tg.putString(layout.leftMargin() + layout.frameWidth() - 1, absRow, "┃");
                    refresh();
                } finally {
                    writeLock.unlock();
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        return resultText -> {
            spinThread.interrupt();
            try { spinThread.join(500); } catch (InterruptedException ignored) {}
            String clean = stripAnsi(resultText);
            writeLock.lock();
            try {
                LanternaComponents.drawContentRow(tg, layout, absRow, clean, palette);
                refresh();
            } finally {
                writeLock.unlock();
            }
        };
    }

    @Override
    public void showError(String message) {
        int absRow = layout.panelContentStart() + currentPanelRow;
        if (absRow > layout.panelContentEnd()) return;
        String clean = stripAnsi(message);
        writeLock.lock();
        try {
            int cx = layout.leftMargin() + 4;
            tg.setForegroundColor(palette.primary());
            tg.putString(layout.leftMargin(), absRow, "┃");
            tg.setForegroundColor(palette.bright());
            tg.putString(cx, absRow, "✗ ");
            tg.setForegroundColor(palette.text());
            tg.putString(cx + 2, absRow, clean);
            int remaining = layout.leftMargin() + layout.frameWidth() - 1 - (cx + 2 + clean.length());
            if (remaining > 0) {
                tg.putString(cx + 2 + clean.length(), absRow, " ".repeat(remaining));
            }
            tg.setForegroundColor(palette.primary());
            tg.putString(layout.leftMargin() + layout.frameWidth() - 1, absRow, "┃");
            refresh();
        } finally {
            writeLock.unlock();
        }
        currentPanelRow++;
    }

    @Override
    public void waitForKey() {
        int absRow = layout.panelContentStart() + currentPanelRow;
        int inputX = layout.leftMargin() + 4;

        writeLock.lock();
        try {
            tg.setForegroundColor(palette.muted());
            tg.putString(inputX, absRow, "[Enter] ");
            refresh();
        } finally {
            writeLock.unlock();
        }

        // Wait for any key
        try {
            screen.readInput();
        } catch (IOException ignored) {}
        currentPanelRow++;
    }

    @Override
    public int getContentWidth() {
        return layout.contentWidth();
    }

    @Override
    public boolean isColorSupported() {
        return true;
    }

    @Override
    public void close() {
        // No-op: screen lifecycle managed by ArcosApplication
        // The screen persists through boot report and greeting phases
    }

    // ── Public accessors for boot phases ────────────────────────────────────

    public Screen getScreen() { return screen; }
    public TextGraphics getTextGraphics() { return tg; }
    public ReentrantLock getWriteLock() { return writeLock; }
    public LanternaPalette getPalette() { return palette; }
    public LayoutCalculator.ScreenLayout getLayout() { return layout; }

    // ── Private helpers ─────────────────────────────────────────────────────

    private void recalculateLayout() {
        TerminalSize newSize = screen.doResizeIfNecessary();
        if (newSize != null) {
            layout = LayoutCalculator.calculate(newSize);
        }
    }

    private void drawStepIndex() {
        List<StepState> states = new ArrayList<>();
        int count = Math.min(5, stepDefs != null ? stepDefs.size() : 0);
        for (int i = 0; i < count; i++) {
            StepDefinition def = stepDefs.get(i);
            StepIndicator.Status status = i < stepStatuses.size()
                    ? stepStatuses.get(i)
                    : StepIndicator.Status.PENDING;
            states.add(new StepState(def.romanNumeral(), def.latinName(), status));
        }
        while (states.size() < 4) {
            states.add(new StepState("", "---", StepIndicator.Status.PENDING));
        }
        LanternaComponents.drawStepIndex(tg, layout, states, palette);
    }

    private void drawPanelDivider() {
        String numeral = "";
        String name = "FIAT";
        if (activeStepIndex >= 0 && activeStepIndex < stepDefs.size()) {
            StepDefinition def = stepDefs.get(activeStepIndex);
            numeral = def.romanNumeral();
            name = def.latinName();
        }
        LanternaComponents.drawPanelDivider(tg, layout, numeral, name, palette);
    }

    private void clearPanelInternal() {
        for (int r = layout.panelContentStart(); r <= layout.panelContentEnd(); r++) {
            LanternaComponents.drawEmptyRow(tg, layout, r, palette);
        }
    }

    private void refresh() {
        try {
            screen.refresh();
        } catch (IOException ignored) {}
    }

    /**
     * Strips ANSI escape codes from text produced by wizard steps.
     * Steps use AnsiPalette constants for color; Lanterna renders via TextColor, not ANSI strings.
     */
    private static String stripAnsi(String text) {
        if (text == null) return "";
        return TerminalCapabilities.strip(text);
    }

    /**
     * Key-by-key input loop using Lanterna's readInput().
     */
    private String readInputLoop(int startX, int row, boolean masked) {
        StringBuilder input = new StringBuilder();
        int maxLen = layout.leftMargin() + layout.frameWidth() - 2 - startX;
        while (true) {
            try {
                KeyStroke key = screen.readInput();
                if (key.getKeyType() == KeyType.Enter) {
                    break;
                } else if (key.getKeyType() == KeyType.Backspace) {
                    if (!input.isEmpty()) {
                        input.deleteCharAt(input.length() - 1);
                        writeLock.lock();
                        try {
                            tg.putString(startX + input.length(), row, " ");
                            screen.setCursorPosition(new TerminalPosition(startX + input.length(), row));
                            refresh();
                        } finally {
                            writeLock.unlock();
                        }
                    }
                } else if (key.getKeyType() == KeyType.Character) {
                    if (input.length() < maxLen) {
                        input.append(key.getCharacter());
                        writeLock.lock();
                        try {
                            String display = masked ? "*" : String.valueOf(key.getCharacter());
                            tg.setForegroundColor(palette.text());
                            tg.putString(startX + input.length() - 1, row, display);
                            screen.setCursorPosition(new TerminalPosition(startX + input.length(), row));
                            refresh();
                        } finally {
                            writeLock.unlock();
                        }
                    }
                } else if (key.getKeyType() == KeyType.EOF) {
                    return null;
                }
            } catch (IOException e) {
                return null;
            }
        }
        return input.toString().trim();
    }
}
