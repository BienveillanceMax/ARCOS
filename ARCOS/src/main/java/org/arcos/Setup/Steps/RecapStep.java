package org.arcos.Setup.Steps;

import org.arcos.Setup.ConfigurationModel;
import org.arcos.Setup.Persistence.ConfigurationWriter;
import org.arcos.Setup.UI.AnsiPalette;
import org.arcos.Setup.UI.TerminalCapabilities;
import org.arcos.Setup.Validation.ApiKeyValidator;
import org.arcos.Setup.WizardContext;
import org.arcos.Setup.WizardStep;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;

/**
 * Étape finale du wizard : récapitulatif de configuration et sauvegarde.
 * Affiche un résumé de toutes les valeurs avant de sauvegarder.
 * Sauvegarde dans .env (permissions 600) et application-local.yaml.
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
        return "Récapitulatif & Sauvegarde";
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
        ConfigurationModel model = context.getModel();

        printHeader(out, color);
        printSummary(out, model, color);

        if (!context.getWarnings().isEmpty()) {
            printWarnings(out, context, color);
        }

        out.println();
        String confirm = readLine(terminal, "  Confirmer la sauvegarde ? [O/n] : ");
        if (confirm != null && (confirm.isBlank() || "o".equalsIgnoreCase(confirm.trim()))) {
            return doSave(out, model, color);
        } else {
            printCancelled(out, color);
            return StepResult.failure("Sauvegarde annulée par l'utilisateur.");
        }
    }

    private StepResult doSave(PrintWriter out, ConfigurationModel model, boolean color) {
        try {
            writer.save(model);
            printSuccess(out, "Configuration sauvegardée dans .env et application-local.yaml", color);
            printSuccess(out, "Relancez ARCOS pour démarrer avec votre configuration.", color);
            return StepResult.success("Configuration sauvegardée.");
        } catch (Exception e) {
            log.error("Erreur lors de la sauvegarde de la configuration : {}", e.getMessage(), e);
            printError(out, "Erreur de sauvegarde : " + e.getMessage(), color);
            return StepResult.failure("Erreur de sauvegarde : " + e.getMessage());
        }
    }

    private void printHeader(PrintWriter out, boolean color) {
        String cyan = color ? AnsiPalette.CYAN : "";
        String bold = color ? AnsiPalette.BOLD : "";
        String reset = color ? AnsiPalette.RESET : "";
        out.println();
        out.println(cyan + bold + "── RÉCAPITULATIF DE LA CONFIGURATION ────────────────────" + reset);
        out.println();
    }

    private void printSummary(PrintWriter out, ConfigurationModel model, boolean color) {
        String amber = color ? AnsiPalette.AMBER : "";
        String green = color ? AnsiPalette.GREEN : "";
        String gray = color ? AnsiPalette.GRAY_LIGHT : "";
        String reset = color ? AnsiPalette.RESET : "";

        out.println(amber + "  Personnalité   " + reset + ": " + orDefault(model.getPersonalityProfile(), "DEFAULT"));

        String micInfo = model.getAudioDeviceIndex() >= 0
                ? "index " + model.getAudioDeviceIndex()
                : "auto-sélection";
        out.println(amber + "  Microphone     " + reset + ": " + micInfo);

        out.println();
        out.println(amber + "  Clés API :" + reset);
        printKeyStatus(out, "MISTRALAI_API_KEY  ", model.getMistralApiKey(), true, green, gray, reset);
        printKeyStatus(out, "BRAVE_SEARCH_API_KEY", model.getBraveSearchApiKey(), false, green, gray, reset);
        printKeyStatus(out, "PORCUPINE_ACCESS_KEY", model.getPorcupineAccessKey(), false, green, gray, reset);

        out.println();
        out.println(gray + "  .env                → " + new ConfigurationWriter().getEnvFile().getAbsolutePath() + reset);
        out.println(gray + "  application-local.yaml → " + new ConfigurationWriter().getLocalYamlFile().getAbsolutePath() + reset);
    }

    private void printKeyStatus(PrintWriter out, String keyName, String value,
                                 boolean required, String green, String gray, String reset) {
        boolean present = value != null && !value.isBlank();
        String status = present
                ? green + "✓ " + ApiKeyValidator.maskKey(value) + reset
                : gray + (required ? "✗ MANQUANTE (obligatoire)" : "— non configurée") + reset;
        out.printf("    %-22s : %s%n", keyName, status);
    }

    private void printWarnings(PrintWriter out, WizardContext context, boolean color) {
        String yellow = color ? AnsiPalette.YELLOW : "";
        String reset = color ? AnsiPalette.RESET : "";
        out.println();
        out.println(yellow + "  ⚠ AVERTISSEMENTS :" + reset);
        for (String warning : context.getWarnings()) {
            out.println("    • " + warning);
        }
    }

    private String readLine(Terminal terminal, String prompt) {
        try {
            LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();
            return reader.readLine(prompt);
        } catch (Exception e) {
            return "o";
        }
    }

    private void printSuccess(PrintWriter out, String message, boolean color) {
        String green = color ? AnsiPalette.GREEN : "";
        String reset = color ? AnsiPalette.RESET : "";
        out.println("  " + green + "✓" + reset + " " + message);
    }

    private void printError(PrintWriter out, String message, boolean color) {
        String red = color ? AnsiPalette.RED : "";
        String reset = color ? AnsiPalette.RESET : "";
        out.println("  " + red + "✗" + reset + " " + message);
    }

    private void printCancelled(PrintWriter out, boolean color) {
        String gray = color ? AnsiPalette.GRAY_LIGHT : "";
        String reset = color ? AnsiPalette.RESET : "";
        out.println("  " + gray + "→ Sauvegarde annulée. Aucun fichier écrit." + reset);
    }

    private String orDefault(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}
