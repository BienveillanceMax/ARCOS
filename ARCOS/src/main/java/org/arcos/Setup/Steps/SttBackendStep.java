package org.arcos.Setup.Steps;

import org.arcos.IO.InputHandling.STT.SttBackendType;
import org.arcos.Setup.StepDefinition;
import org.arcos.Setup.UI.AnsiPalette;
import org.arcos.Setup.UI.WizardDisplay;
import org.arcos.Setup.WizardContext;
import org.arcos.Setup.WizardStep;

/**
 * Step III — INTERPRES: STT backend selection.
 * - FASTER_WHISPER: local CPU inference (Raspberry Pi compatible)
 * - WHISPER_CPP: GPU inference via ROCm/Vulkan (server with iGPU/dGPU)
 */
public class SttBackendStep implements WizardStep {

    private record BackendOption(SttBackendType type, String description) {}

    private static final BackendOption[] BACKENDS = {
            new BackendOption(SttBackendType.FASTER_WHISPER,
                    "Local CPU inference — Raspberry Pi compatible"),
            new BackendOption(SttBackendType.WHISPER_CPP,
                    "GPU inference (ROCm/Vulkan) — server with iGPU/dGPU")
    };

    @Override
    public String getName() {
        return "INTERPRES";
    }

    @Override
    public boolean isRequired() {
        return false;
    }

    @Override
    public boolean isSkippable() {
        return true;
    }

    @Override
    public StepDefinition getStepDefinition() {
        return StepDefinition.INTERPRES;
    }

    @Override
    public StepResult execute(WizardDisplay display, WizardContext context) {
        boolean color = display.isColorSupported();

        display.printLine("Select speech-to-text backend:");
        display.printLine("");

        for (int i = 0; i < BACKENDS.length; i++) {
            BackendOption b = BACKENDS[i];
            String nameColor = color ? AnsiPalette.BRIGHT : "";
            String reset = color ? AnsiPalette.RESET : "";

            display.printLine("[" + (i + 1) + "]  " + nameColor + b.type().name() + reset
                    + " \u2014 " + b.description());
        }

        display.printLine("");

        SttBackendType currentBackend = context.getModel().getSttBackend();
        String defaultHint = " [current: " + currentBackend.name() + "]";

        while (true) {
            String input = display.readLine("\u25b8 (1-2 or 's' to skip)" + defaultHint + " ");

            if (input == null || input.isBlank()) {
                display.printLine(okText("Backend kept: " + currentBackend.name(), color));
                return StepResult.success("Backend kept: " + currentBackend.name());
            }

            if ("s".equalsIgnoreCase(input.trim()) || "skip".equalsIgnoreCase(input.trim())) {
                return StepResult.skipped("STT backend skipped — default: " + currentBackend.name());
            }

            try {
                int choice = Integer.parseInt(input.trim());
                if (choice >= 1 && choice <= BACKENDS.length) {
                    BackendOption chosen = BACKENDS[choice - 1];
                    context.getModel().setSttBackend(chosen.type());

                    if (chosen.type() == SttBackendType.WHISPER_CPP) {
                        String currentUrl = context.getModel().getSttWhisperCppUrl();
                        if (currentUrl == null || currentUrl.isBlank()) {
                            currentUrl = "http://localhost:8090";
                        }
                        String urlHint = " [default: " + currentUrl + "]";

                        display.printLine("");
                        String urlInput = display.readLine("\u25b8 whisper.cpp URL" + urlHint + " ");

                        if (urlInput == null || urlInput.isBlank()) {
                            context.getModel().setSttWhisperCppUrl(currentUrl);
                        } else {
                            context.getModel().setSttWhisperCppUrl(urlInput.trim());
                        }

                        display.printLine(okText("Backend: " + chosen.type().name()
                                + " @ " + context.getModel().getSttWhisperCppUrl(), color));
                        return StepResult.success("Backend: " + chosen.type().name()
                                + " @ " + context.getModel().getSttWhisperCppUrl());
                    }

                    display.printLine(okText("Backend selected: " + chosen.type().name(), color));
                    return StepResult.success("Backend: " + chosen.type().name());
                } else {
                    display.showError("Choose between 1 and " + BACKENDS.length + ".");
                }
            } catch (NumberFormatException e) {
                display.showError("Enter a number (1-2) or 's' to skip.");
            }
        }
    }

    private String okText(String msg, boolean color) {
        if (color) return AnsiPalette.OK + "\u2713" + AnsiPalette.RESET + " " + msg;
        return "[OK] " + msg;
    }
}
