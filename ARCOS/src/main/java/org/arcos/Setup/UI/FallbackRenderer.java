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

/**
 * Scrolling println-based fallback for TERM=dumb, pipes, or terminals below minimum size.
 * ASCII borders, no ANSI codes, no alternate screen buffer.
 * Latin nomenclature and structure preserved.
 */
public class FallbackRenderer implements WizardDisplay {

    private static final char[] SPINNER_FRAMES_PLAIN = {'/', '-', '\\', '|'};
    private static final int SPINNER_INTERVAL_MS = 150;

    private final Terminal terminal;
    private final PrintWriter out;
    private final boolean color;
    private List<StepDefinition> stepDefs;
    private final List<StepIndicator.Status> stepStatuses = new ArrayList<>();

    public FallbackRenderer(Terminal terminal) {
        this.terminal = terminal;
        this.out = terminal.writer();
        this.color = TerminalCapabilities.isColorSupported();
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
        int width = 60;
        out.println();
        out.println(BoxDrawing.headerBar(width, !color));
        out.println();

        drawStepIndex();

        out.println();
        out.flush();
    }

    @Override
    public void activateStep(int i) {
        if (i < stepStatuses.size()) {
            stepStatuses.set(i, StepIndicator.Status.ACTIVE);
        }
        String numeral = "";
        String name = "FIAT";
        if (i < stepDefs.size()) {
            numeral = stepDefs.get(i).romanNumeral();
            name = stepDefs.get(i).latinName();
        }

        out.println();
        out.println(BoxDrawing.panelDivider(numeral, name, 60, !color));
        out.println();
        out.flush();
    }

    @Override
    public void completeStep(int i) {
        if (i < stepStatuses.size()) {
            stepStatuses.set(i, StepIndicator.Status.COMPLETED);
        }
    }

    @Override
    public void printLine(int row, String text) {
        // In fallback mode, row is ignored — sequential output
        out.println("   " + text);
        out.flush();
    }

    @Override
    public void printLine(String text) {
        out.println("   " + text);
        out.flush();
    }

    @Override
    public void clearPanel() {
        out.println();
    }

    @Override
    public void statusLine(String label, String value, String detail, StatusColor statusColor) {
        String line = StatusLineRenderer.render(label, value, detail, statusColor, 54, color);
        out.println("   " + line);
        out.flush();
    }

    @Override
    public void gauge(String label, int value, int labelWidth) {
        out.println("   " + GaugeRenderer.render(label, value, labelWidth, color));
        out.flush();
    }

    @Override
    public String gaugeCompact(String abbreviation, int value) {
        return GaugeRenderer.renderCompact(abbreviation, value, color);
    }

    @Override
    public String readLine(String prompt) {
        try {
            LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();
            String input = reader.readLine("   " + prompt);
            return input != null ? input.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String readMaskedLine(String prompt) {
        try {
            LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();
            String input = reader.readLine("   " + prompt, '*');
            return input != null ? input.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public SpinnerHandle showSpinner(String label) {
        out.print("   ");
        out.flush();

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "spinner");
            t.setDaemon(true);
            return t;
        });

        final int[] frameCounter = {0};
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            char spinChar = SPINNER_FRAMES_PLAIN[frameCounter[0] % SPINNER_FRAMES_PLAIN.length];
            frameCounter[0]++;
            out.print("\r   " + spinChar + "  " + label + "  ");
            out.flush();
        }, 0, SPINNER_INTERVAL_MS, TimeUnit.MILLISECONDS);

        return resultText -> {
            task.cancel(false);
            scheduler.shutdownNow();
            out.println("\r   " + resultText);
            out.flush();
        };
    }

    @Override
    public void showError(String message) {
        String prefix = color ? AnsiPalette.BRIGHT + "✗" + AnsiPalette.RESET : "[!!]";
        out.println("   " + prefix + " " + message);
        out.flush();
    }

    @Override
    public void waitForKey() {
        readLine("[Enter] ");
    }

    @Override
    public int getContentWidth() {
        return 54; // 60 - 6 for margins
    }

    @Override
    public boolean isColorSupported() {
        return color;
    }

    @Override
    public void close() {
        out.println();
        out.println(BoxDrawing.footer(60, !color));
        out.println();
        out.flush();
    }

    private void drawStepIndex() {
        List<StepState> states = new ArrayList<>();
        int count = Math.min(4, stepDefs != null ? stepDefs.size() : 0);
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

        String[] lines = StepIndicator.renderStepIndex(states, 54, color);
        for (String line : lines) {
            out.println("   " + line);
        }
    }
}
