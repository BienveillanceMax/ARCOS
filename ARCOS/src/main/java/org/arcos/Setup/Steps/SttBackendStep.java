package org.arcos.Setup.Steps;

import org.arcos.IO.InputHandling.STT.SttBackendType;
import org.arcos.Setup.StepDefinition;
import org.arcos.Setup.UI.StatusColor;
import org.arcos.Setup.UI.WizardDisplay;
import org.arcos.Setup.UI.WizardDisplay.MenuItem;
import org.arcos.Setup.WizardContext;
import org.arcos.Setup.WizardStep;

import java.util.List;

/**
 * Step III — INTERPRES: STT backend selection.
 * - FASTER_WHISPER: local CPU inference (Raspberry Pi compatible)
 * - WHISPER_CPP: GPU inference via ROCm/Vulkan (server with iGPU/dGPU)
 */
public class SttBackendStep implements WizardStep {

    private static final SttBackendType[] BACKENDS = {
            SttBackendType.FASTER_WHISPER,
            SttBackendType.WHISPER_CPP
    };

    private static final List<MenuItem> BACKEND_ITEMS = List.of(
            new MenuItem("FASTER-WHISPER", "CPU inference · Raspberry Pi class"),
            new MenuItem("WHISPER-CPP", "GPU inference · ROCm/Vulkan server"));

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
        display.setKeyHints("↑↓ NAV · ↵ SELECT · ESC BACK");

        SttBackendType currentBackend = context.getModel().getSttBackend();
        int defaultIndex = currentBackend == SttBackendType.WHISPER_CPP ? 1 : 0;

        display.printLine("SPEECH-TO-TEXT ENGINE");
        display.printLine("");

        int choice = display.selectMenu(BACKEND_ITEMS, defaultIndex);
        if (choice == WizardDisplay.MENU_BACK) {
            return StepResult.BACK;
        }

        SttBackendType chosen = BACKENDS[choice];
        context.getModel().setSttBackend(chosen);

        if (chosen == SttBackendType.WHISPER_CPP) {
            String currentUrl = context.getModel().getSttWhisperCppUrl();
            if (currentUrl == null || currentUrl.isBlank()) {
                currentUrl = "http://localhost:8090";
            }

            display.setKeyHints("↵ CONFIRM · EMPTY = DEFAULT");
            display.printLine("");
            String urlInput = display.readLine("ENDPOINT [" + currentUrl + "] ▸ ");

            String url = (urlInput == null || urlInput.isBlank()) ? currentUrl : urlInput.trim();
            context.getModel().setSttWhisperCppUrl(url);

            display.printLine("");
            display.statusLine("INTERPRES", chosen.name(), url, StatusColor.OK);
            return StepResult.success("Backend: " + chosen.name() + " @ " + url);
        }

        return StepResult.success("Backend: " + chosen.name());
    }
}
