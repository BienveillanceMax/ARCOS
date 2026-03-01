package org.arcos.Setup;

import org.arcos.Setup.Detection.ConfigurationDetector;
import org.arcos.Setup.Steps.ApiKeyStep;
import org.arcos.Setup.Steps.AudioDeviceStep;
import org.arcos.Setup.Steps.PersonalityStep;
import org.arcos.Setup.Steps.RecapStep;
import org.arcos.Setup.Steps.ServiceCheckStep;
import org.arcos.Setup.UI.FallbackRenderer;
import org.arcos.Setup.UI.ScreenManager;
import org.arcos.Setup.UI.TerminalCapabilities;
import org.arcos.Setup.UI.WizardDisplay;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Orchestrates the ARCOS configuration wizard.
 * Runs BEFORE SpringApplication.run() — no Spring dependencies.
 *
 * Detects rendering mode:
 * - Full-screen (ScreenManager) when alternate screen supported and terminal large enough
 * - Fallback (FallbackRenderer) for TERM=dumb, pipe, or small terminals
 *
 * Usage: WizardRunner.runIfNeeded(args) in ArcosApplication.main()
 */
public class WizardRunner {

    private static final Logger log = LoggerFactory.getLogger(WizardRunner.class);

    private WizardRunner() {}

    /**
     * Launches the wizard if configuration is incomplete or --setup flag is passed.
     *
     * @param args command line arguments
     * @return true if the wizard ran and saved configuration successfully
     */
    public static boolean runIfNeeded(String[] args) {
        boolean forceSetup = containsArg(args, "--setup") || containsArg(args, "--reconfigure");
        ConfigurationDetector detector = new ConfigurationDetector();
        boolean configMissing = !detector.isConfigurationComplete();

        if (!forceSetup && !configMissing) {
            log.debug("Configuration complete — wizard not needed.");
            return false;
        }

        if (System.console() == null) {
            if (configMissing) {
                log.error("ARCOS configuration incomplete and no interactive terminal available.");
                log.error("Create a .env file with MISTRALAI_API_KEY=your_key then restart ARCOS.");
            } else {
                log.debug("Wizard --setup requested but no TTY available — ignored.");
            }
            return false;
        }

        if (configMissing) {
            log.info("ARCOS configuration incomplete — launching configuration wizard.");
        } else {
            log.info("Relaunching configuration wizard (--setup).");
        }

        ConfigurationModel existingConfig = detector.loadExistingConfiguration();
        WizardContext context = new WizardContext(existingConfig);

        try {
            return runWizard(context);
        } catch (Exception e) {
            log.error("Unexpected error in wizard: {}", e.getMessage(), e);
            System.err.println("Error in configuration wizard. " +
                    "Configure manually via .env (see .env.example).");
            return false;
        }
    }

    /**
     * Runs the interactive wizard with JLine3.
     */
    static boolean runWizard(WizardContext context) {
        try (Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .jansi(true)
                .build()) {

            // Select rendering mode
            boolean fullScreen = TerminalCapabilities.isFullScreenSupported()
                    && TerminalCapabilities.isMinimumWidthMet(terminal)
                    && TerminalCapabilities.isMinimumHeightMet(terminal);

            WizardDisplay display = fullScreen
                    ? new ScreenManager(terminal)
                    : new FallbackRenderer(terminal);

            try {
                List<StepDefinition> stepDefs = List.of(
                        StepDefinition.NEXUS, StepDefinition.VOX,
                        StepDefinition.ANIMA, StepDefinition.CORPUS,
                        StepDefinition.FIAT);

                display.initializeSteps(stepDefs);
                display.drawFrame();

                List<WizardStep> steps = List.of(
                        new ApiKeyStep(),
                        new AudioDeviceStep(),
                        new PersonalityStep(),
                        new ServiceCheckStep(),
                        new RecapStep()
                );

                for (int i = 0; i < steps.size(); i++) {
                    WizardStep step = steps.get(i);
                    display.activateStep(i);

                    WizardStep.StepResult result = step.execute(display, context);

                    if (result.success()) {
                        display.completeStep(i);
                    } else {
                        if (step.isRequired() && !result.skipped()) {
                            log.warn("Step '{}' failed: {}", step.getName(), result.message());

                            // Check if user wants to restart (RecapStep returns failure for cancel)
                            if ("FIAT".equals(step.getName()) && result.message().contains("cancelled")) {
                                display.close();
                                // Restart wizard
                                return runWizard(context);
                            }

                            display.showError("Wizard interrupted: " + result.message());
                            display.printLine("Configure manually via .env (see .env.example).");
                            display.waitForKey();
                            return false;
                        }
                    }
                }

                return true;
            } finally {
                display.close();
            }

        } catch (IOException e) {
            log.error("Cannot initialize JLine3 terminal: {}", e.getMessage());
            System.err.println("Interactive terminal not available. " +
                    "Configure manually via .env (see .env.example).");
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
}
