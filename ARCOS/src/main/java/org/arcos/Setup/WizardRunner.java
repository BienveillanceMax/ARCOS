package org.arcos.Setup;

import org.arcos.Setup.UI.AnsiPalette;
import org.arcos.Setup.UI.TerminalCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestre le wizard de configuration interactif d'ARCOS.
 * Tourne AVANT SpringApplication.run() — aucune dépendance Spring.
 *
 * Usage : WizardRunner.runIfNeeded(args) dans ArcosApplication.main()
 */
public class WizardRunner {

    private static final Logger log = LoggerFactory.getLogger(WizardRunner.class);

    private WizardRunner() {}

    /**
     * Lance le wizard si la configuration est incomplète ou si --setup est passé en argument.
     *
     * @param args arguments de la ligne de commande
     * @return true si le wizard a été exécuté (et que Spring doit redémarrer la config)
     */
    public static boolean runIfNeeded(String[] args) {
        boolean forceSetup = containsArg(args, "--setup") || containsArg(args, "--reconfigure");
        boolean configMissing = !isConfigurationComplete();

        if (!forceSetup && !configMissing) {
            return false;
        }

        log.info("Lancement du wizard de configuration ARCOS...");
        printWelcomeBanner();

        // TODO STORY-001 à STORY-007 : implémenter les étapes du wizard
        log.warn("Wizard non encore implémenté. Configurez manuellement votre .env.");

        return false; // Pour l'instant, le wizard ne bloque pas le démarrage
    }

    private static boolean containsArg(String[] args, String target) {
        if (args == null) return false;
        for (String arg : args) {
            if (target.equals(arg)) return true;
        }
        return false;
    }

    private static boolean isConfigurationComplete() {
        String mistralKey = System.getenv("MISTRALAI_API_KEY");
        return mistralKey != null && !mistralKey.isBlank();
    }

    private static void printWelcomeBanner() {
        boolean color = TerminalCapabilities.isColorSupported();
        String orange = color ? AnsiPalette.ORANGE_BRIGHT : "";
        String reset = color ? AnsiPalette.RESET : "";
        String bold = color ? AnsiPalette.BOLD : "";

        System.out.println(orange + "╔══════════════════════════════════════════════════════╗" + reset);
        System.out.println(orange + "║  " + bold + "A R C O S" + reset + orange + "  —  Wizard de Configuration          ║" + reset);
        System.out.println(orange + "╚══════════════════════════════════════════════════════╝" + reset);
        System.out.println();
    }
}
