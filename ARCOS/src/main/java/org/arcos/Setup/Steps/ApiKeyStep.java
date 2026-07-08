package org.arcos.Setup.Steps;

import org.arcos.Setup.ConfigurationModel;
import org.arcos.Setup.StepDefinition;
import org.arcos.Setup.UI.StatusColor;
import org.arcos.Setup.UI.WizardDisplay;
import org.arcos.Setup.UI.WizardDisplay.MenuItem;
import org.arcos.Setup.Validation.ApiKeyValidator;
import org.arcos.Setup.WizardContext;
import org.arcos.Setup.WizardStep;

import java.util.List;

/**
 * Step I — NEXUS: API key entry and validation.
 * - MISTRALAI_API_KEY: mandatory, validated via HTTP, fixed retry region
 * - BRAVE_SEARCH_API_KEY / PORCUPINE_ACCESS_KEY: optional, CONFIGURE/SKIP menu
 *
 * Fixed row map (panel-relative) — regions rewrite in place, never creep:
 *   0  MISTRALAI_API_KEY ........ REQUIRED
 *   1    KEY ▸ input
 *   2    validation status
 *   4  BRAVE_SEARCH_API_KEY ..... OPTIONAL · WEB SEARCH
 *   5-6  menu CONFIGURE / SKIP
 *   7    input → result
 *   9  PORCUPINE_ACCESS_KEY ..... OPTIONAL · WAKE WORD
 *   10-11 menu CONFIGURE / SKIP
 *   12   input → result
 */
public class ApiKeyStep implements WizardStep {

    private static final List<MenuItem> CONFIGURE_OR_SKIP = List.of(
            new MenuItem("CONFIGURE", "enter and validate key"),
            new MenuItem("SKIP", "feature stays disabled"));

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
        ConfigurationModel model = context.getModel();

        // ── Mistral AI (mandatory, in-place retry region rows 0-2) ──────────
        display.setKeyHints("↵ CONFIRM · KEYS MASKED");
        display.setRow(0);
        display.statusLine("MISTRALAI_API_KEY", "REQUIRED", null, StatusColor.INFO);

        boolean mistralOk = false;
        while (!mistralOk) {
            String existingKey = model.getMistralApiKey();

            display.printLine(1, "");
            display.printLine(2, "");
            display.setRow(1);
            String key = display.readMaskedLine(buildPrompt(existingKey));

            // Enter with an existing key keeps it
            if ((key == null || key.isBlank()) && existingKey != null && !existingKey.isBlank()) {
                key = existingKey;
            }

            if (key == null || key.isBlank()) {
                display.setRow(2);
                display.showError("Mistral AI key is required.");
                continue;
            }

            display.setRow(2);
            WizardDisplay.SpinnerHandle spinner = display.showSpinner("VALIDATING MISTRAL AI");
            ApiKeyValidator.ValidationResult result = validator.validateMistralKey(key);

            if (result.valid()) {
                model.setMistralApiKey(key);
                model.setKeyValidated("MISTRALAI_API_KEY", true);
                spinner.stop("✓ VALIDATED  " + ApiKeyValidator.maskKey(key));
                mistralOk = true;
            } else {
                spinner.stop("✗ INVALID — " + result.message());
            }
        }

        // ── Brave Search (optional) ─────────────────────────────────────────
        display.setKeyHints("↑↓ NAV · ↵ SELECT · ESC BACK");
        int braveResult = configureOptionalKey(display, "BRAVE_SEARCH_API_KEY",
                "OPTIONAL · WEB SEARCH", 4,
                model.getBraveSearchApiKey(),
                key -> {
                    ApiKeyValidator.ValidationResult r = validator.validateBraveKey(key);
                    if (r.valid()) {
                        model.setBraveSearchApiKey(key);
                        model.setKeyValidated("BRAVE_SEARCH_API_KEY", true);
                    }
                    return r;
                },
                context, "web search disabled");
        if (braveResult == WizardDisplay.MENU_BACK) return StepResult.BACK;

        // ── Porcupine (optional) ────────────────────────────────────────────
        int porcResult = configureOptionalKey(display, "PORCUPINE_ACCESS_KEY",
                "OPTIONAL · WAKE WORD", 9,
                model.getPorcupineAccessKey(),
                key -> {
                    ApiKeyValidator.ValidationResult r = validator.validatePorcupineKey(key);
                    if (r.valid()) {
                        model.setPorcupineAccessKey(key);
                        model.setKeyValidated("PORCUPINE_ACCESS_KEY", true);
                    }
                    return r;
                },
                context, "wake word disabled");
        if (porcResult == WizardDisplay.MENU_BACK) return StepResult.BACK;

        return StepResult.success("API keys configured.");
    }

    /**
     * Renders one optional key block: header, CONFIGURE/SKIP menu, input+result row.
     *
     * @return 0 on completion, {@link WizardDisplay#MENU_BACK} if the user backed out
     */
    private int configureOptionalKey(WizardDisplay display, String keyName, String annotation,
                                     int baseRow, String existingValue,
                                     java.util.function.Function<String, ApiKeyValidator.ValidationResult> validate,
                                     WizardContext context, String disabledEffect) {
        display.setRow(baseRow);
        display.statusLine(keyName, annotation, null, StatusColor.INFO);

        boolean hasExisting = existingValue != null && !existingValue.isBlank();
        display.setRow(baseRow + 1);
        int choice = display.selectMenu(CONFIGURE_OR_SKIP, hasExisting ? 0 : 1);

        if (choice == WizardDisplay.MENU_BACK) {
            return WizardDisplay.MENU_BACK;
        }

        int resultRow = baseRow + 3;
        if (choice == 0) {
            display.setRow(resultRow);
            String key = display.readMaskedLine(buildPrompt(existingValue));
            if ((key == null || key.isBlank()) && hasExisting) {
                key = existingValue;
            }
            if (key != null && !key.isBlank()) {
                display.setRow(resultRow);
                WizardDisplay.SpinnerHandle spinner = display.showSpinner("VALIDATING");
                ApiKeyValidator.ValidationResult result = validate.apply(key);
                if (result.valid()) {
                    spinner.stop("✓ VALIDATED  " + ApiKeyValidator.maskKey(key));
                } else {
                    spinner.stop("✗ INVALID — " + result.message() + " · not saved");
                    context.addWarning(keyName + " invalid — " + disabledEffect);
                }
                return 0;
            }
        }

        context.addWarning(keyName + " not configured — " + disabledEffect);
        display.setRow(resultRow);
        display.printLine("— SKIPPED · " + disabledEffect);
        return 0;
    }

    private String buildPrompt(String existingValue) {
        if (existingValue != null && !existingValue.isBlank()) {
            return "KEY [" + ApiKeyValidator.maskKey(existingValue) + "] ▸ ";
        }
        return "KEY ▸ ";
    }
}
