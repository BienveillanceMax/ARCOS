package org.arcos.Setup.Steps;

import org.arcos.Setup.ConfigurationModel;
import org.arcos.Setup.Persistence.ConfigurationWriter;
import org.arcos.Setup.StepDefinition;
import org.arcos.Setup.UI.AnsiPalette;
import org.arcos.Setup.UI.StatusColor;
import org.arcos.Setup.UI.WizardDisplay;
import org.arcos.Setup.Validation.ApiKeyValidator;
import org.arcos.Setup.WizardContext;
import org.arcos.Setup.WizardStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Final step — FIAT: Configuration summary and save.
 * Displays summary in dot-leader format. English text.
 * Post-save: FIAT LUX. — the final moment.
 */
public class RecapStep implements WizardStep {

    private static final Logger log = LoggerFactory.getLogger(RecapStep.class);

    private final ConfigurationWriter writer;

    public RecapStep() {
        this.writer = new ConfigurationWriter();
    }

    RecapStep(ConfigurationWriter writer) {
        this.writer = writer;
    }

    @Override
    public String getName() {
        return "FIAT";
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
        return StepDefinition.FIAT;
    }

    @Override
    public StepResult execute(WizardDisplay display, WizardContext context) {
        boolean color = display.isColorSupported();
        ConfigurationModel model = context.getModel();

        // Summary in dot-leader format
        String profile = orDefault(model.getPersonalityProfile(), "DEFAULT");
        display.statusLine("ANIMA", profile, null, StatusColor.INFO);

        String micInfo = model.getAudioDeviceIndex() >= 0
                ? "index " + model.getAudioDeviceIndex()
                : "auto-select";
        display.statusLine("VOX", micInfo, null, StatusColor.INFO);

        display.printLine("");

        // API keys
        printKeyStatus(display, "MISTRALAI_API_KEY", model.getMistralApiKey(), true, color);
        printKeyStatus(display, "BRAVE_SEARCH_API_KEY", model.getBraveSearchApiKey(), false, color);
        printKeyStatus(display, "PORCUPINE_ACCESS_KEY", model.getPorcupineAccessKey(), false, color);

        display.printLine("");

        // File paths
        String envPath = writer.getEnvFile().getAbsolutePath();
        String yamlPath = writer.getLocalYamlFile().getAbsolutePath();
        display.statusLine(".env", envPath, null, StatusColor.MUTED);
        display.statusLine("application-local.yaml", yamlPath, null, StatusColor.MUTED);

        // Warnings
        if (!context.getWarnings().isEmpty()) {
            display.printLine("");
            for (String warning : context.getWarnings()) {
                display.printLine(warnText(warning, color));
            }
        }

        display.printLine("");

        // Confirmation
        String input = display.readLine("[Y] Confirm   [A] Cancel and restart   \u25b8 ");
        if (input == null || input.isBlank() || "y".equalsIgnoreCase(input.trim())) {
            return doSave(display, model, color);
        } else {
            display.printLine(mutedText("Cancelled. No files written.", color));
            return StepResult.failure("Save cancelled by user.");
        }
    }

    private StepResult doSave(WizardDisplay display, ConfigurationModel model, boolean color) {
        try {
            writer.save(model);

            display.printLine(okText(".env", color));
            display.printLine(okText("application-local.yaml", color));
            display.printLine("");

            // The final moment
            if (color) {
                display.printLine(AnsiPalette.BRIGHT + AnsiPalette.BOLD + "FIAT LUX." + AnsiPalette.RESET);
            } else {
                display.printLine("FIAT LUX.");
            }

            display.printLine("");
            display.waitForKey();

            return StepResult.success("Configuration saved.");
        } catch (Exception e) {
            log.error("Error saving configuration: {}", e.getMessage(), e);
            display.showError("Save error: " + e.getMessage());
            return StepResult.failure("Save error: " + e.getMessage());
        }
    }

    private void printKeyStatus(WizardDisplay display, String keyName, String value,
                                boolean required, boolean color) {
        boolean present = value != null && !value.isBlank();
        if (present) {
            display.statusLine(keyName, "\u2713 " + ApiKeyValidator.maskKey(value), null, StatusColor.OK);
        } else {
            String status = required ? "\u2717 MISSING (required)" : "\u2014 not configured";
            StatusColor sColor = required ? StatusColor.BRIGHT : StatusColor.MUTED;
            display.statusLine(keyName, status, null, sColor);
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

    private String mutedText(String msg, boolean color) {
        if (color) return AnsiPalette.MUTED + "\u2192" + AnsiPalette.RESET + " " + msg;
        return "-> " + msg;
    }

    private String orDefault(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}
