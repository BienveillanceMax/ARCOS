package org.arcos.Setup.Steps;

import org.arcos.Setup.StepDefinition;
import org.arcos.Setup.UI.StatusColor;
import org.arcos.Setup.UI.WizardDisplay;
import org.arcos.Setup.UI.WizardDisplay.MenuItem;
import org.arcos.Setup.Validation.AudioDeviceEnumerator;
import org.arcos.Setup.WizardContext;
import org.arcos.Setup.WizardStep;

import java.util.ArrayList;
import java.util.List;

/**
 * Step II — VOX: Microphone selection.
 * Probes every input device for live signal (RMS), then presents an
 * interactive menu with per-device signal readouts. The loudest device is
 * pre-selected; AUTO delegates to system selection.
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
        display.setKeyHints("↑↓ NAV · ↵ SELECT · ESC BACK");

        List<AudioDeviceEnumerator.AudioDevice> devices = AudioDeviceEnumerator.getInputDevices();

        if (devices.isEmpty()) {
            display.statusLine("INPUT DEVICES", "NONE DETECTED", null, StatusColor.WARN);
            display.printLine("System auto-selection will be used.");
            context.getModel().setAudioDeviceIndex(-1);
            context.addWarning("No microphone detected — audio index = -1 (auto)");
            display.printLine("");
            display.waitForKey();
            return StepResult.success("No microphone detected — auto mode.");
        }

        if (devices.size() == 1) {
            AudioDeviceEnumerator.AudioDevice device = devices.get(0);
            context.getModel().setAudioDeviceIndex(device.index());
            context.getModel().setAudioDeviceName(device.name());
            display.statusLine("VOX", device.name() + " · index " + device.index(),
                    "auto-bound", StatusColor.OK);
            // Follow-through: let the readout land before the next step clears it
            try { Thread.sleep(700); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            return StepResult.success("Auto-selected: " + device.name());
        }

        // ── Probe all devices for signal while a spinner runs ───────────────
        // ALSA without PulseAudio allows one open subdevice per card at a time:
        // first pass probes all, second pass retries the locked ones.
        display.setRow(0);
        WizardDisplay.SpinnerHandle spinner = display.showSpinner("VOX PROBE — SAMPLING INPUT SIGNAL");

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

        int bestMenuIndex = -1;
        int bestRms = 0;
        for (int d = 0; d < devices.size(); d++) {
            if (results[d].rms() > bestRms) {
                bestRms = results[d].rms();
                bestMenuIndex = d;
            }
        }

        spinner.stop(String.format("%d DEVICES · SIGNAL SAMPLED", devices.size()));
        display.printLine("");

        // ── Device menu with signal annotations ─────────────────────────────
        List<MenuItem> items = new ArrayList<>();
        for (int d = 0; d < devices.size(); d++) {
            AudioDeviceEnumerator.AudioDevice device = devices.get(d);
            items.add(new MenuItem(truncate(device.name(), 30), signalAnnotation(results[d])));
        }
        items.add(new MenuItem("AUTO", "system default selection"));

        int currentIndex = context.getModel().getAudioDeviceIndex();
        int defaultMenuIndex = menuIndexOfDevice(devices, currentIndex);
        if (defaultMenuIndex < 0) defaultMenuIndex = bestMenuIndex;
        if (defaultMenuIndex < 0) defaultMenuIndex = 0;

        int choice = display.selectMenu(items, defaultMenuIndex);
        if (choice == WizardDisplay.MENU_BACK) {
            return StepResult.BACK;
        }

        if (choice == items.size() - 1) {
            context.getModel().setAudioDeviceIndex(-1);
            return StepResult.success("Microphone: system auto-selection.");
        }

        AudioDeviceEnumerator.AudioDevice chosen = devices.get(choice);
        context.getModel().setAudioDeviceIndex(chosen.index());
        context.getModel().setAudioDeviceName(chosen.name());
        return StepResult.success("Microphone configured: " + chosen.name());
    }

    private static int menuIndexOfDevice(List<AudioDeviceEnumerator.AudioDevice> devices, int deviceIndex) {
        if (deviceIndex < 0) return -1;
        for (int d = 0; d < devices.size(); d++) {
            if (devices.get(d).index() == deviceIndex) return d;
        }
        return -1;
    }

    private static String signalAnnotation(AudioDeviceEnumerator.ProbeResult probe) {
        int rms = probe.rms();
        if (rms < 0) {
            return probe.error() != null ? "— ERROR · " + probe.error() : "— ERROR";
        }
        if (rms == 0) {
            return "— SILENT";
        }
        if (rms < 20) {
            return "▁▂  LOW · rms " + rms;
        }
        return "▁▂▃ LIVE · rms " + rms;
    }

    private static String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 1) + "…";
    }
}
