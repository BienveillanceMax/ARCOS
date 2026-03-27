package org.arcos.Setup;

import com.googlecode.lanterna.screen.Screen;
import org.arcos.Setup.Detection.ConfigurationDetector;
import org.arcos.Setup.Steps.ApiKeyStep;
import org.arcos.Setup.Steps.AudioDeviceStep;
import org.arcos.Setup.Steps.PersonalityStep;
import org.arcos.Setup.Steps.RecapStep;
import org.arcos.Setup.Steps.ServiceCheckStep;
import org.arcos.Setup.Steps.SttBackendStep;
import org.arcos.Setup.UI.FallbackRenderer;
import org.arcos.Setup.UI.LanternaScreenManager;
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
 * Two entry points:
 * - runWizard(Screen): full-screen Lanterna path (called from unified boot flow)
 * - runIfNeededFallback(args): TERM=dumb fallback path (scrolling output)
 */
public class WizardRunner {

    private static final Logger log = LoggerFactory.getLogger(WizardRunner.class);

    private WizardRunner() {}

    /**
     * Runs the wizard in the existing Lanterna screen (full-screen path).
     * The screen is already started and showing the welcome screen.
     * The screen is NOT closed here — it persists for the boot report.
     *
     * @param screen the active Lanterna screen
     * @return true if the wizard completed successfully
     */
    public static boolean runWizard(Screen screen) {
        ConfigurationDetector detector = new ConfigurationDetector();
        ConfigurationModel existingConfig = detector.loadExistingConfiguration();
        WizardContext context = new WizardContext(existingConfig);

        LanternaScreenManager display = new LanternaScreenManager(screen);
        return executeWizardSteps(display, context);
    }

    /**
     * Fallback-only path for TERM=dumb or piped environments.
     * Checks if config is missing and runs the fallback wizard if needed.
     *
     * @param args command line arguments
     * @return true if the wizard ran and saved configuration successfully
     */
    public static boolean runIfNeededFallback(String[] args) {
        ConfigurationDetector detector = new ConfigurationDetector();
        boolean configMissing = !detector.isConfigurationComplete();

        if (!configMissing) {
            log.debug("Configuration complete — wizard not needed.");
            return false;
        }

        if (System.console() == null) {
            log.error("ARCOS configuration incomplete and no interactive terminal available.");
            log.error("Create a .env file with MISTRALAI_API_KEY=your_key then restart ARCOS.");
            return false;
        }

        log.info("ARCOS configuration incomplete — launching configuration wizard (fallback mode).");

        ConfigurationModel existingConfig = detector.loadExistingConfiguration();
        WizardContext context = new WizardContext(existingConfig);

        try (Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .jansi(true)
                .build()) {

            FallbackRenderer display = new FallbackRenderer(terminal);
            try {
                return executeWizardSteps(display, context);
            } finally {
                display.close();
            }

        } catch (IOException e) {
            log.error("Cannot initialize terminal: {}", e.getMessage());
            System.err.println("Interactive terminal not available. " +
                    "Configure manually via .env (see .env.example).");
            return false;
        }
    }

    /**
     * Common wizard step execution logic, shared by both Lanterna and fallback paths.
     */
    private static boolean executeWizardSteps(WizardDisplay display, WizardContext context) {
        List<StepDefinition> stepDefs = List.of(
                StepDefinition.NEXUS, StepDefinition.VOX,
                StepDefinition.INTERPRES, StepDefinition.ANIMA,
                StepDefinition.CORPUS, StepDefinition.FIAT);

        display.initializeSteps(stepDefs);
        display.drawFrame();

        List<WizardStep> steps = List.of(
                new ApiKeyStep(),
                new AudioDeviceStep(),
                new SttBackendStep(),
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
                    if ("FIAT".equals(step.getName()) && result.message().contains("cancelled")) {
                        return executeWizardSteps(display, context);
                    }

                    log.warn("Step '{}' failed: {}", step.getName(), result.message());
                    display.showError("Wizard interrupted: " + result.message());
                    display.printLine("Configure manually via .env (see .env.example).");
                    display.waitForKey();
                    return false;
                }
            }
        }

        return true;
    }
}
