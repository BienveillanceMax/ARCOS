package org.arcos.UnitTests.Setup;

import org.arcos.Setup.StepDefinition;
import org.arcos.Setup.UI.GaugeRenderer;
import org.arcos.Setup.UI.StatusColor;
import org.arcos.Setup.UI.StatusLineRenderer;
import org.arcos.Setup.UI.WizardDisplay;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Recording mock implementation of WizardDisplay for step tests.
 * Records all calls and provides pre-configured input responses.
 */
public class MockWizardDisplay implements WizardDisplay {

    private final List<String> printedLines = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private final List<String> statusLines = new ArrayList<>();
    private final List<String> spinnerLabels = new ArrayList<>();
    private final Queue<String> inputResponses = new LinkedList<>();
    private final List<Integer> activatedSteps = new ArrayList<>();
    private final List<Integer> completedSteps = new ArrayList<>();
    private int currentRow = 0;
    private boolean closed = false;

    /** Pre-load input responses that readLine/readMaskedLine will return. */
    public void addInputResponse(String response) {
        inputResponses.add(response);
    }

    public List<String> getPrintedLines() { return printedLines; }
    public List<String> getErrors() { return errors; }
    public List<String> getStatusLines() { return statusLines; }
    public List<String> getSpinnerLabels() { return spinnerLabels; }
    public List<Integer> getActivatedSteps() { return activatedSteps; }
    public List<Integer> getCompletedSteps() { return completedSteps; }
    public boolean isClosed() { return closed; }

    @Override public void initializeSteps(List<StepDefinition> steps) {}
    @Override public void drawFrame() {}

    @Override public void activateStep(int i) { activatedSteps.add(i); }
    @Override public void completeStep(int i) { completedSteps.add(i); }

    @Override
    public void printLine(int row, String text) {
        printedLines.add("[" + row + "] " + text);
    }

    @Override
    public void printLine(String text) {
        printedLines.add(text);
        currentRow++;
    }

    @Override public void clearPanel() { currentRow = 0; }

    @Override
    public void statusLine(String label, String value, String detail, StatusColor statusColor) {
        statusLines.add(label + " -> " + value + (detail != null ? " " + detail : ""));
    }

    @Override
    public void gauge(String label, int value, int labelWidth) {
        printedLines.add("GAUGE: " + label + " = " + value);
    }

    @Override
    public String gaugeCompact(String abbreviation, int value) {
        return GaugeRenderer.renderCompact(abbreviation, value, false);
    }

    @Override
    public String readLine(String prompt) {
        printedLines.add("PROMPT: " + prompt);
        return inputResponses.isEmpty() ? null : inputResponses.poll();
    }

    @Override
    public String readMaskedLine(String prompt) {
        printedLines.add("MASKED: " + prompt);
        return inputResponses.isEmpty() ? null : inputResponses.poll();
    }

    @Override
    public SpinnerHandle showSpinner(String label) {
        spinnerLabels.add(label);
        return resultText -> printedLines.add("SPINNER_RESULT: " + resultText);
    }

    @Override
    public void showError(String message) {
        errors.add(message);
    }

    @Override public void waitForKey() {}
    @Override public int getContentWidth() { return 54; }
    @Override public boolean isColorSupported() { return false; }
    @Override public void close() { closed = true; }
}
