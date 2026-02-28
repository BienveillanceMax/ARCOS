package org.arcos.Setup.Steps;

import org.arcos.Setup.ConfigurationModel;
import org.arcos.Setup.UI.AnsiPalette;
import org.arcos.Setup.UI.TerminalCapabilities;
import org.arcos.Setup.Validation.ApiKeyValidator;
import org.arcos.Setup.WizardContext;
import org.arcos.Setup.WizardStep;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;

import java.io.PrintWriter;

/**
 * Étape 1 du wizard : saisie et validation des clés API.
 * - MISTRALAI_API_KEY : obligatoire, validé par appel HTTP
 * - BRAVE_SEARCH_API_KEY : optionnel, skippable
 * - PORCUPINE_ACCESS_KEY : optionnel, skippable
 *
 * Saisie masquée via JLine3 LineReader.readLine(prompt, mask).
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
        return "Clés API";
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
    public StepResult execute(Terminal terminal, WizardContext context) {
        PrintWriter out = terminal.writer();
        boolean color = TerminalCapabilities.isColorSupported();

        printHeader(out, color);

        ConfigurationModel model = context.getModel();
        boolean allMandatoryOk = false;

        // ── Clé Mistral AI (obligatoire) ─────────────────────────────────────
        while (!allMandatoryOk) {
            String existingKey = model.getMistralApiKey();
            String prompt = buildPrompt("MISTRALAI_API_KEY", true, existingKey, color);
            String key = readMaskedLine(terminal, prompt, existingKey);

            if (key == null || key.isBlank()) {
                printError(out, "La clé Mistral AI est obligatoire.", color);
                continue;
            }

            printValidating(out, "Mistral AI", color);
            ApiKeyValidator.ValidationResult result = validator.validateMistralKey(key);

            if (result.valid()) {
                model.setMistralApiKey(key);
                model.setKeyValidated("MISTRALAI_API_KEY", true);
                printSuccess(out, "Clé Mistral AI valide (" + ApiKeyValidator.maskKey(key) + ")", color);
                allMandatoryOk = true;
            } else {
                printError(out, "Clé invalide : " + result.message(), color);
                out.println("  Réessayez ou vérifiez votre clé sur https://console.mistral.ai/");
            }
        }

        // ── Clé Brave Search (optionnel) ────────────────────────────────────
        out.println();
        printOptionalPrompt(out, "BRAVE_SEARCH_API_KEY", "Recherche web (Chercher_sur_Internet)", color);
        String braveChoice = readChoice(terminal, out, color);
        if ("1".equals(braveChoice)) {
            String existingBrave = model.getBraveSearchApiKey();
            String prompt = buildPrompt("BRAVE_SEARCH_API_KEY", false, existingBrave, color);
            String key = readMaskedLine(terminal, prompt, existingBrave);
            if (key != null && !key.isBlank()) {
                printValidating(out, "Brave Search", color);
                ApiKeyValidator.ValidationResult result = validator.validateBraveKey(key);
                if (result.valid()) {
                    model.setBraveSearchApiKey(key);
                    model.setKeyValidated("BRAVE_SEARCH_API_KEY", true);
                    printSuccess(out, "Clé Brave Search valide (" + ApiKeyValidator.maskKey(key) + ")", color);
                } else {
                    printError(out, "Clé invalide : " + result.message() + " — clé non sauvegardée.", color);
                    context.addWarning("BRAVE_SEARCH_API_KEY invalide — recherche web désactivée");
                }
            }
        } else {
            context.addWarning("BRAVE_SEARCH_API_KEY non configuré — recherche web désactivée");
            printSkipped(out, "Brave Search ignoré — recherche web désactivée.", color);
        }

        // ── Clé Porcupine (optionnel) ────────────────────────────────────────
        out.println();
        printOptionalPrompt(out, "PORCUPINE_ACCESS_KEY", "Détection du mot de réveil", color);
        String porcupineChoice = readChoice(terminal, out, color);
        if ("1".equals(porcupineChoice)) {
            String existingPorc = model.getPorcupineAccessKey();
            String prompt = buildPrompt("PORCUPINE_ACCESS_KEY", false, existingPorc, color);
            String key = readMaskedLine(terminal, prompt, existingPorc);
            if (key != null && !key.isBlank()) {
                ApiKeyValidator.ValidationResult result = validator.validatePorcupineKey(key);
                if (result.valid()) {
                    model.setPorcupineAccessKey(key);
                    model.setKeyValidated("PORCUPINE_ACCESS_KEY", true);
                    printSuccess(out, "Clé Porcupine enregistrée (" + ApiKeyValidator.maskKey(key) + ")", color);
                } else {
                    printError(out, "Clé invalide : " + result.message() + " — clé non sauvegardée.", color);
                    context.addWarning("PORCUPINE_ACCESS_KEY invalide — wake word désactivé");
                }
            }
        } else {
            context.addWarning("PORCUPINE_ACCESS_KEY non configuré — wake word désactivé");
            printSkipped(out, "Porcupine ignoré — détection du mot de réveil désactivée.", color);
        }

        return StepResult.success("Clés API configurées.");
    }

    private void printHeader(PrintWriter out, boolean color) {
        String cyan = color ? AnsiPalette.CYAN : "";
        String bold = color ? AnsiPalette.BOLD : "";
        String reset = color ? AnsiPalette.RESET : "";
        out.println();
        out.println(cyan + bold + "── ÉTAPE 1 : Clés API ──────────────────────────────────" + reset);
        out.println("  Entrez vos clés API pour activer les services ARCOS.");
        out.println("  Les clés sont masquées à la saisie (••••) et stockées dans .env (permissions 600).");
        out.println();
    }

    private void printOptionalPrompt(PrintWriter out, String keyName, String description, boolean color) {
        String amber = color ? AnsiPalette.AMBER : "";
        String reset = color ? AnsiPalette.RESET : "";
        out.println(amber + keyName + reset + " (" + description + ") — optionnel");
        out.println("  [1] Entrer la clé   [2] Passer");
    }

    private String buildPrompt(String keyName, boolean required, String existingValue, boolean color) {
        String amber = color ? AnsiPalette.AMBER : "";
        String reset = color ? AnsiPalette.RESET : "";
        String suffix = (existingValue != null && !existingValue.isBlank())
                ? " [actuel: " + ApiKeyValidator.maskKey(existingValue) + "]"
                : "";
        String marker = required ? " *" : "";
        return amber + keyName + marker + reset + suffix + " : ";
    }

    private String readMaskedLine(Terminal terminal, String prompt, String defaultValue) {
        try {
            LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();
            String input = reader.readLine(prompt, '*');
            if (input != null) input = input.trim();
            // Si l'utilisateur appuie sur Entrée sans rien taper et qu'il y a une valeur existante, la conserver
            if ((input == null || input.isBlank()) && defaultValue != null && !defaultValue.isBlank()) {
                return defaultValue;
            }
            return input;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String readChoice(Terminal terminal, PrintWriter out, boolean color) {
        try {
            LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();
            out.print("  Choix : ");
            out.flush();
            String input = reader.readLine("  Choix : ");
            if (input != null) return input.trim();
            return "2";
        } catch (Exception e) {
            return "2";
        }
    }

    private void printValidating(PrintWriter out, String service, boolean color) {
        String amber = color ? AnsiPalette.AMBER : "";
        String reset = color ? AnsiPalette.RESET : "";
        out.print("  " + amber + "⟳" + reset + " Validation " + service + "...");
        out.flush();
    }

    private void printSuccess(PrintWriter out, String message, boolean color) {
        String green = color ? AnsiPalette.GREEN : "";
        String reset = color ? AnsiPalette.RESET : "";
        out.println("\r  " + green + "✓" + reset + " " + message);
    }

    private void printError(PrintWriter out, String message, boolean color) {
        String red = color ? AnsiPalette.RED : "";
        String reset = color ? AnsiPalette.RESET : "";
        out.println("\r  " + red + "✗" + reset + " " + message);
    }

    private void printSkipped(PrintWriter out, String message, boolean color) {
        String gray = color ? AnsiPalette.GRAY_LIGHT : "";
        String reset = color ? AnsiPalette.RESET : "";
        out.println("  " + gray + "→" + reset + " " + message);
    }
}
