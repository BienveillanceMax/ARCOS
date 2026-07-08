package org.arcos.Setup.Steps;

import org.arcos.Setup.ConfigurationModel;
import org.arcos.Setup.Persistence.ConfigurationWriter;
import org.arcos.Setup.StepDefinition;
import org.arcos.Setup.UI.StatusColor;
import org.arcos.Setup.UI.WizardDisplay;
import org.arcos.Setup.UI.WizardDisplay.MenuItem;
import org.arcos.Setup.Validation.ApiKeyValidator;
import org.arcos.Setup.WizardContext;
import org.arcos.Setup.WizardStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Final step — SIGILLUM: Configuration summary and save.
 * Dense dot-leader recap of every collected value, then a SEAL / REVISE
 * decision menu. On write: file confirmations + FACTUM EST. reveal.
 */
public class RecapStep implements WizardStep {

    private static final Logger log = LoggerFactory.getLogger(RecapStep.class);

    private static final List<MenuItem> DECISION = List.of(
            new MenuItem("SEAL", "write configuration and proceed"),
            new MenuItem("REVISE", "restart the wizard, values kept"));

    private final ConfigurationWriter writer;

    public RecapStep() {
        this.writer = new ConfigurationWriter();
    }

    RecapStep(ConfigurationWriter writer) {
        this.writer = writer;
    }

    @Override
    public String getName() {
        return "SIGILLUM";
    }

    @Override
    public boolean isRequired() {
        return true;
    }

    @Override
    public boolean isSkippable() {
        return false;
    }

    @Override
    public StepDefinition getStepDefinition() {
        return StepDefinition.SIGILLUM;
    }

    @Override
    public StepResult execute(WizardDisplay display, WizardContext context) {
        display.setKeyHints("↑↓ NAV · ↵ SELECT · ESC BACK");
        ConfigurationModel model = context.getModel();

        // ── Identity & bindings ─────────────────────────────────────────────
        display.statusLine("ANIMA", orDefault(model.getPersonalityProfile(), "DEFAULT"),
                null, StatusColor.INFO);
        display.statusLine("VOX", model.getAudioDeviceIndex() >= 0
                        ? "index " + model.getAudioDeviceIndex()
                        : "auto-select",
                null, StatusColor.INFO);
        display.statusLine("INTERPRES", model.getSttBackend().name(), null, StatusColor.INFO);

        display.printLine("");

        // ── API keys ────────────────────────────────────────────────────────
        printKeyStatus(display, "MISTRALAI_API_KEY", model.getMistralApiKey(), true);
        printKeyStatus(display, "BRAVE_SEARCH_API_KEY", model.getBraveSearchApiKey(), false);
        printKeyStatus(display, "PORCUPINE_ACCESS_KEY", model.getPorcupineAccessKey(), false);

        display.printLine("");

        // ── Target files ────────────────────────────────────────────────────
        display.statusLine(".env", writer.getEnvFile().getAbsolutePath(),
                null, StatusColor.MUTED);
        display.statusLine("application-local.yaml", writer.getLocalYamlFile().getAbsolutePath(),
                null, StatusColor.MUTED);

        display.printLine("");

        // ── Decision ────────────────────────────────────────────────────────
        int choice = display.selectMenu(DECISION, 0);
        if (choice == WizardDisplay.MENU_BACK) {
            return StepResult.BACK;
        }
        if (choice == 1) {
            return StepResult.failure("Save cancelled by user.");
        }
        return doSave(display, model);
    }

    private StepResult doSave(WizardDisplay display, ConfigurationModel model) {
        try {
            writer.save(model);

            display.printLine("");
            display.statusLine(".env", "✓ WRITTEN", "chmod 600", StatusColor.OK);
            display.statusLine("application-local.yaml", "✓ WRITTEN", null, StatusColor.OK);
            display.printLine("");

            // The final moment — earned, mechanical, no fanfare
            display.reveal("FACTUM EST.");

            display.printLine("");
            display.setKeyHints("↵ BOOT");
            display.waitForKey();

            return StepResult.success("Configuration saved.");
        } catch (Exception e) {
            log.error("Error saving configuration: {}", e.getMessage(), e);
            display.showError("Save error: " + e.getMessage());
            return StepResult.failure("Save error: " + e.getMessage());
        }
    }

    private void printKeyStatus(WizardDisplay display, String keyName, String value,
                                boolean required) {
        boolean present = value != null && !value.isBlank();
        if (present) {
            display.statusLine(keyName, "✓ " + ApiKeyValidator.maskKey(value), null, StatusColor.OK);
        } else {
            String status = required ? "✗ MISSING (required)" : "— not configured";
            StatusColor sColor = required ? StatusColor.BRIGHT : StatusColor.MUTED;
            display.statusLine(keyName, status, null, sColor);
        }
    }

    private String orDefault(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}
