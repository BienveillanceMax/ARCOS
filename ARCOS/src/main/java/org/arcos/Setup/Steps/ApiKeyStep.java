package org.arcos.Setup.Steps;

import org.arcos.Setup.ConfigurationModel;
import org.arcos.Setup.StepDefinition;
import org.arcos.Setup.UI.AnsiPalette;
import org.arcos.Setup.UI.StatusColor;
import org.arcos.Setup.UI.WizardDisplay;
import org.arcos.Setup.Validation.ApiKeyValidator;
import org.arcos.Setup.WizardContext;
import org.arcos.Setup.WizardStep;

/**
 * Step I — NEXUS: API key entry and validation.
 * - MISTRALAI_API_KEY: mandatory, validated via HTTP
 * - BRAVE_SEARCH_API_KEY: optional, skippable
 * - PORCUPINE_ACCESS_KEY: optional, skippable
 *
 * All text in English. Masked input via display.readMaskedLine().
 * Fixed 3-line validation region that rewrites in place on retry.
 */
public class ApiKeyStep implements WizardStep {

    private final ApiKeyValidator validator;

    public ApiKeyStep() {
        this.validator = new ApiKeyValidator();
    }

    ApiKeyStep(ApiKeyValidator validator) {
        this.validator = validator;
    }

    @Override
    public String getName() {
        return "NEXUS";
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
        return StepDefinition.NEXUS;
    }

    @Override
    public StepResult execute(WizardDisplay display, WizardContext context) {
        boolean color = display.isColorSupported();
        ConfigurationModel model = context.getModel();

        display.printLine("Enter your API keys. Keys are masked and stored in .env (permissions 600).");
        display.printLine("");

        // ── Mistral AI (mandatory) ──────────────────────────────────────────
        display.printLine("MISTRALAI_API_KEY *");
        int mistralInputRow = 3; // fixed row for retry region

        boolean mistralOk = false;
        while (!mistralOk) {
            String existingKey = model.getMistralApiKey();
            String prompt = buildPrompt(existingKey, color);
            display.printLine(mistralInputRow, ""); // clear line
            String key = display.readMaskedLine(prompt);

            // If user pressed Enter with existing key, keep it
            if ((key == null || key.isBlank()) && existingKey != null && !existingKey.isBlank()) {
                key = existingKey;
            }

            if (key == null || key.isBlank()) {
                display.printLine(mistralInputRow + 1, errorText("Mistral AI key is required.", color));
                continue;
            }

            WizardDisplay.SpinnerHandle spinner = display.showSpinner("Validating Mistral AI...");
            ApiKeyValidator.ValidationResult result = validator.validateMistralKey(key);

            if (result.valid()) {
                model.setMistralApiKey(key);
                model.setKeyValidated("MISTRALAI_API_KEY", true);
                spinner.stop(okText("Key validated (" + ApiKeyValidator.maskKey(key) + ")", color));
                mistralOk = true;
            } else {
                spinner.stop(errorText("Invalid key: " + result.message(), color));
            }
        }

        display.printLine("");

        // ── Brave Search (optional) ─────────────────────────────────────────
        display.printLine("BRAVE_SEARCH_API_KEY [OPTIONAL]");
        display.printLine("[1] Enter key   [2] Skip");
        String braveChoice = display.readLine("\u25b8 ");

        if ("1".equals(braveChoice)) {
            String existingBrave = model.getBraveSearchApiKey();
            String key = display.readMaskedLine(buildPrompt(existingBrave, color));
            if ((key == null || key.isBlank()) && existingBrave != null && !existingBrave.isBlank()) {
                key = existingBrave;
            }
            if (key != null && !key.isBlank()) {
                WizardDisplay.SpinnerHandle spinner = display.showSpinner("Validating Brave Search...");
                ApiKeyValidator.ValidationResult result = validator.validateBraveKey(key);
                if (result.valid()) {
                    model.setBraveSearchApiKey(key);
                    model.setKeyValidated("BRAVE_SEARCH_API_KEY", true);
                    spinner.stop(okText("Key validated (" + ApiKeyValidator.maskKey(key) + ")", color));
                } else {
                    spinner.stop(errorText("Invalid key: " + result.message() + " — not saved.", color));
                    context.addWarning("BRAVE_SEARCH_API_KEY invalid — web search disabled");
                }
            }
        } else {
            context.addWarning("BRAVE_SEARCH_API_KEY not configured — web search disabled");
            display.printLine(mutedText("Skipped — web search disabled", color));
        }

        display.printLine("");

        // ── Porcupine (optional) ────────────────────────────────────────────
        display.printLine("PORCUPINE_ACCESS_KEY [OPTIONAL]");
        display.printLine("[1] Enter key   [2] Skip");
        String porcChoice = display.readLine("\u25b8 ");

        if ("1".equals(porcChoice)) {
            String existingPorc = model.getPorcupineAccessKey();
            String key = display.readMaskedLine(buildPrompt(existingPorc, color));
            if ((key == null || key.isBlank()) && existingPorc != null && !existingPorc.isBlank()) {
                key = existingPorc;
            }
            if (key != null && !key.isBlank()) {
                ApiKeyValidator.ValidationResult result = validator.validatePorcupineKey(key);
                if (result.valid()) {
                    model.setPorcupineAccessKey(key);
                    model.setKeyValidated("PORCUPINE_ACCESS_KEY", true);
                    display.printLine(okText("Key registered (" + ApiKeyValidator.maskKey(key) + ")", color));
                } else {
                    display.showError("Invalid key: " + result.message() + " — not saved.");
                    context.addWarning("PORCUPINE_ACCESS_KEY invalid — wake word disabled");
                }
            }
        } else {
            context.addWarning("PORCUPINE_ACCESS_KEY not configured — wake word disabled");
            display.printLine(mutedText("Skipped — wake word disabled", color));
        }

        return StepResult.success("API keys configured.");
    }

    private String buildPrompt(String existingValue, boolean color) {
        String suffix = (existingValue != null && !existingValue.isBlank())
                ? " [current: " + ApiKeyValidator.maskKey(existingValue) + "]"
                : "";
        return suffix + " : ";
    }

    private String okText(String msg, boolean color) {
        if (color) return AnsiPalette.OK + "\u2713" + AnsiPalette.RESET + " " + msg;
        return "[OK] " + msg;
    }

    private String errorText(String msg, boolean color) {
        if (color) return AnsiPalette.BRIGHT + "\u2717" + AnsiPalette.RESET + " " + msg;
        return "[!!] " + msg;
    }

    private String mutedText(String msg, boolean color) {
        if (color) return AnsiPalette.MUTED + "\u2192" + AnsiPalette.RESET + " " + msg;
        return "-> " + msg;
    }
}
