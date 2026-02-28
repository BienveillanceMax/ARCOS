package org.arcos.Setup;

import org.arcos.Setup.Detection.ConfigurationDetector;
import org.arcos.Setup.Steps.ApiKeyStep;
import org.arcos.Setup.Steps.AudioDeviceStep;
import org.arcos.Setup.Steps.PersonalityStep;
import org.arcos.Setup.Steps.RecapStep;
import org.arcos.Setup.Steps.ServiceCheckStep;
import org.arcos.Setup.UI.AnsiPalette;
import org.arcos.Setup.UI.TerminalCapabilities;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * Orchestre le wizard de configuration interactif d'ARCOS.
 * Tourne AVANT SpringApplication.run() — aucune dépendance Spring.
 *
 * Usage : WizardRunner.runIfNeeded(args) dans ArcosApplication.main()
 * Retourne true si le wizard a été exécuté avec succès et que Spring peut démarrer.
 * Retourne false si le wizard a été annulé ou si la configuration est déjà complète.
 */
public class WizardRunner {

    private static final Logger log = LoggerFactory.getLogger(WizardRunner.class);

    private WizardRunner() {}

    /**
     * Lance le wizard si la configuration est incomplète ou si --setup est passé en argument.
     * STORY-001 : détection automatique de configuration manquante.
     * STORY-023 : relancement du wizard à la demande via --setup / --reconfigure.
     *
     * @param args arguments de la ligne de commande
     * @return true si le wizard a été exécuté et la configuration sauvegardée avec succès
     */
    public static boolean runIfNeeded(String[] args) {
        boolean forceSetup = containsArg(args, "--setup") || containsArg(args, "--reconfigure");
        ConfigurationDetector detector = new ConfigurationDetector();
        boolean configMissing = !detector.isConfigurationComplete();

        if (!forceSetup && !configMissing) {
            log.debug("Configuration complète — wizard non nécessaire.");
            return false;
        }

        // Vérifier qu'un terminal interactif est disponible (pas de TTY = CI, redirection, daemon)
        if (System.console() == null) {
            if (configMissing) {
                log.error("Configuration ARCOS incomplète et aucun terminal interactif disponible.");
                log.error("Créez un fichier .env avec MISTRALAI_API_KEY=votre_clé puis relancez ARCOS.");
            } else {
                log.debug("Wizard --setup demandé mais aucun TTY disponible — ignoré.");
            }
            return false;
        }

        if (configMissing) {
            log.info("Configuration ARCOS incomplète — lancement du wizard de configuration.");
        } else {
            log.info("Relancement du wizard de configuration (--setup).");
        }

        // Charger la configuration existante pour pré-remplir le wizard
        ConfigurationModel existingConfig = detector.loadExistingConfiguration();
        WizardContext context = new WizardContext(existingConfig);

        try {
            return runWizard(context);
        } catch (Exception e) {
            log.error("Erreur inattendue dans le wizard : {}", e.getMessage(), e);
            System.err.println("Erreur dans le wizard de configuration. " +
                    "Configurez manuellement votre .env (voir .env.example).");
            return false;
        }
    }

    /**
     * Exécute le wizard interactif avec JLine3.
     *
     * @param context contexte mutable du wizard (peut contenir une config existante)
     * @return true si la configuration a été sauvegardée avec succès
     */
    static boolean runWizard(WizardContext context) {
        try (Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .jansi(true)
                .build()) {

            PrintWriter out = terminal.writer();
            printWelcomeBanner(out);

            // Étapes du wizard dans l'ordre
            List<WizardStep> steps = List.of(
                    new ApiKeyStep(),
                    new AudioDeviceStep(),
                    new PersonalityStep(),
                    new ServiceCheckStep(),
                    new RecapStep()
            );

            for (WizardStep step : steps) {
                WizardStep.StepResult result = step.execute(terminal, context);
                if (!result.success()) {
                    log.warn("Étape '{}' échouée : {}", step.getName(), result.message());
                    if (step.isRequired() && !result.skipped()) {
                        out.println();
                        String red = TerminalCapabilities.isColorSupported() ? AnsiPalette.RED : "";
                        String reset = TerminalCapabilities.isColorSupported() ? AnsiPalette.RESET : "";
                        out.println(red + "  ✗ Wizard interrompu : " + result.message() + reset);
                        out.println("  Configurez manuellement votre .env (voir .env.example).");
                        out.flush();
                        return false;
                    }
                }
            }

            out.flush();
            return true;

        } catch (IOException e) {
            log.error("Impossible d'initialiser le terminal JLine3 : {}", e.getMessage());
            // Fallback : affichage simple sans couleurs
            System.err.println("Terminal interactif non disponible. " +
                    "Configurez manuellement votre .env (voir .env.example).");
            return false;
        }
    }

    private static boolean containsArg(String[] args, String target) {
        if (args == null) return false;
        for (String arg : args) {
            if (target.equals(arg)) return true;
        }
        return false;
    }

    private static void printWelcomeBanner(PrintWriter out) {
        boolean color = TerminalCapabilities.isColorSupported();
        String orange = color ? AnsiPalette.ORANGE_BRIGHT : "";
        String amber = color ? AnsiPalette.AMBER : "";
        String reset = color ? AnsiPalette.RESET : "";
        String bold = color ? AnsiPalette.BOLD : "";
        String gray = color ? AnsiPalette.GRAY_LIGHT : "";

        out.println();
        out.println(orange + "╔══════════════════════════════════════════════════════╗" + reset);
        out.println(orange + "║    " + bold + "A R C O S" + reset + orange + "  —  Wizard de Configuration             ║" + reset);
        out.println(orange + "╚══════════════════════════════════════════════════════╝" + reset);
        out.println();
        out.println(gray + "  Ce wizard vous guidera pour configurer ARCOS en ~10 minutes." + reset);
        out.println(gray + "  Les clés API sont stockées dans .env (permissions 600)." + reset);
        out.println(gray + "  Pour reconfigurer plus tard : java -jar arcos.jar --setup" + reset);
        out.println();
        out.flush();
    }
}
