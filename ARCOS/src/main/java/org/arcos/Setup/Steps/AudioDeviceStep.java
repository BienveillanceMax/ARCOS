package org.arcos.Setup.Steps;

import org.arcos.Setup.StepDefinition;
import org.arcos.Setup.UI.AnsiPalette;
import org.arcos.Setup.UI.WizardDisplay;
import org.arcos.Setup.Validation.AudioDeviceEnumerator;
import org.arcos.Setup.WizardContext;
import org.arcos.Setup.WizardStep;

import java.util.List;

/**
 * Step II — VOX: Microphone selection.
 * - Auto-selects if only 1 device
 * - Numbered menu if multiple devices
 * All text in English.
 */
public class AudioDeviceStep implements WizardStep {

    @Override
    public String getName() {
        return "VOX";
    }

    @Override
    public boolean isRequired() {
        return true;
    }

    @Override
    public boolean isSkippable() {
        return true;
    }

    @Override
    public StepDefinition getStepDefinition() {
        return StepDefinition.VOX;
    }

    @Override
    public StepResult execute(WizardDisplay display, WizardContext context) {
        boolean color = display.isColorSupported();

        List<AudioDeviceEnumerator.AudioDevice> devices = AudioDeviceEnumerator.getInputDevices();

        if (devices.isEmpty()) {
            display.printLine(warnText("No audio input device detected.", color));
            display.printLine("ARCOS will use system auto-selection.");
            context.getModel().setAudioDeviceIndex(-1);
            context.addWarning("No microphone detected — audio index = -1 (auto)");
            return StepResult.success("No microphone detected — auto mode.");
        }

        if (devices.size() == 1) {
            AudioDeviceEnumerator.AudioDevice device = devices.get(0);
            context.getModel().setAudioDeviceIndex(device.index());
            String msg = "Auto-selected: " + device.name() + " (index " + device.index() + ")";
            display.printLine(okText(msg, color));
            return StepResult.success(msg);
        }

        // Multiple devices — show menu
        display.printLine("Select microphone:");
        display.printLine("");
        for (AudioDeviceEnumerator.AudioDevice device : devices) {
            display.printLine("[" + device.index() + "]  " + device.name());
        }
        display.printLine("");

        int currentIndex = context.getModel().getAudioDeviceIndex();
        String defaultHint = currentIndex >= 0 ? " [current: " + currentIndex + "]" : "";

        while (true) {
            String input = display.readLine("\u25b8" + defaultHint + " ");
            if (input == null || input.isBlank()) {
                if (currentIndex >= 0) {
                    display.printLine(okText("Kept index: " + currentIndex, color));
                    return StepResult.success("Microphone kept: index " + currentIndex);
                }
                display.showError("Enter a valid index.");
                continue;
            }
            if ("s".equalsIgnoreCase(input.trim()) || "skip".equalsIgnoreCase(input.trim())) {
                context.getModel().setAudioDeviceIndex(-1);
                return StepResult.skipped("Audio selection skipped — auto mode.");
            }
            try {
                int idx = Integer.parseInt(input.trim());
                AudioDeviceEnumerator.AudioDevice chosen = AudioDeviceEnumerator.getDeviceAt(idx);
                if (chosen != null) {
                    context.getModel().setAudioDeviceIndex(idx);
                    display.printLine(okText("Selected: " + chosen.name() + " (index " + idx + ")", color));
                    return StepResult.success("Microphone configured: index " + idx);
                } else {
                    display.showError("Index " + idx + " not found. Choose from the listed indices.");
                }
            } catch (NumberFormatException e) {
                display.showError("Enter a number or 's' to skip.");
            }
        }
    }

    private String okText(String msg, boolean color) {
        if (color) return AnsiPalette.OK + "\u2713" + AnsiPalette.RESET + " " + msg;
        return "[OK] " + msg;
    }

    private String warnText(String msg, boolean color) {
        if (color) return AnsiPalette.WARN + "\u26a0" + AnsiPalette.RESET + " " + msg;
        return "[!!] " + msg;
    }
}
