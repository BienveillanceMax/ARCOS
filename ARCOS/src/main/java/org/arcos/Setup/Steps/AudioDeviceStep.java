package org.arcos.Setup.Steps;

import org.arcos.Setup.StepDefinition;
import org.arcos.Setup.UI.AnsiPalette;
import org.arcos.Setup.UI.WizardDisplay;
import org.arcos.Setup.Validation.AudioDeviceEnumerator;
import org.arcos.Setup.WizardContext;
import org.arcos.Setup.WizardStep;

import java.util.ArrayList;
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
            context.getModel().setAudioDeviceName(device.name());
            String msg = "Auto-selected: " + device.name() + " (index " + device.index() + ")";
            display.printLine(okText(msg, color));
            return StepResult.success(msg);
        }

        // Multiple devices — probe each for signal.
        // ALSA without PulseAudio only allows one subdevice open per card at a time.
        // First pass probes all; second pass retries failures (card lock released by then).
        display.printLine("Probing microphones...");

        int bestIndex = -1;
        int bestRms = -1;

        AudioDeviceEnumerator.ProbeResult[] results = new AudioDeviceEnumerator.ProbeResult[devices.size()];
        List<Integer> retryIndices = new ArrayList<>();

        for (int d = 0; d < devices.size(); d++) {
            results[d] = AudioDeviceEnumerator.probeRmsLevel(devices.get(d).index(), 44100, 300);
            if (results[d].rms() < 0) {
                retryIndices.add(d);
            }
        }

        if (!retryIndices.isEmpty()) {
            try { Thread.sleep(500); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            for (int d : retryIndices) {
                results[d] = AudioDeviceEnumerator.probeRmsLevel(devices.get(d).index(), 44100, 300);
            }
        }

        display.printLine("");
        for (int d = 0; d < devices.size(); d++) {
            AudioDeviceEnumerator.AudioDevice device = devices.get(d);
            String indicator = signalIndicator(results[d], color);
            display.printLine("[" + device.index() + "]  " + device.name() + "  " + indicator);
            if (results[d].rms() > bestRms) {
                bestRms = results[d].rms();
                bestIndex = device.index();
            }
        }
        display.printLine("");

        String recommendation = "";
        if (bestRms > 0 && bestIndex >= 0) {
            recommendation = " [recommended: " + bestIndex + "]";
        }

        int currentIndex = context.getModel().getAudioDeviceIndex();
        String defaultHint = currentIndex >= 0 ? " [current: " + currentIndex + "]" : recommendation;

        while (true) {
            String input = display.readLine("\u25b8 (index or 's' to skip)" + defaultHint + " ");
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
                    context.getModel().setAudioDeviceName(chosen.name());
                    display.printLine(okText("Selected: " + chosen.name() + " (index " + idx + ")", color));
                    return StepResult.success("Microphone configured: " + chosen.name());
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

    private String signalIndicator(AudioDeviceEnumerator.ProbeResult probe, boolean color) {
        int rms = probe.rms();
        if (rms < 0) {
            String detail = probe.error() != null ? " (" + probe.error() + ")" : "";
            return color
                    ? AnsiPalette.MUTED + "--- ERROR" + detail + AnsiPalette.RESET
                    : "--- ERROR" + detail;
        }
        if (rms == 0) {
            return color
                    ? AnsiPalette.MUTED + "--- SILENT" + AnsiPalette.RESET
                    : "--- SILENT";
        }
        // Scale: 1-20 = low, 20+ = live
        if (rms < 20) {
            return color
                    ? AnsiPalette.WARN + "\u2581\u2582  LOW (rms:" + rms + ")" + AnsiPalette.RESET
                    : "..  LOW (rms:" + rms + ")";
        }
        return color
                ? AnsiPalette.OK + "\u2581\u2582\u2583 LIVE (rms:" + rms + ")" + AnsiPalette.RESET
                : "... LIVE (rms:" + rms + ")";
    }
}
