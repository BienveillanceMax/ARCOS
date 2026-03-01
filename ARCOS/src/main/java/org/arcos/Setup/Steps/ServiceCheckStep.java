package org.arcos.Setup.Steps;

import org.arcos.Setup.Health.FasterWhisperHealthChecker;
import org.arcos.Setup.Health.HealthResult;
import org.arcos.Setup.Health.MistralHealthChecker;
import org.arcos.Setup.Health.PiperHealthChecker;
import org.arcos.Setup.Health.PorcupineHealthChecker;
import org.arcos.Setup.Health.QdrantHealthChecker;
import org.arcos.Setup.Health.ServiceHealthCheck;
import org.arcos.Setup.Health.ServiceStatus;
import org.arcos.Setup.StepDefinition;
import org.arcos.Setup.UI.AnsiPalette;
import org.arcos.Setup.UI.StatusColor;
import org.arcos.Setup.UI.WizardDisplay;
import org.arcos.Setup.WizardContext;
import org.arcos.Setup.WizardStep;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Step IV — CORPUS: Service connectivity verification.
 * Sequential reveal with spinner per service. All text in English.
 * Status vocabulary: ONLINE / DEFICIT / DEGRADED.
 */
public class ServiceCheckStep implements WizardStep {

    @Override
    public String getName() {
        return "CORPUS";
    }

    @Override
    public boolean isRequired() {
        return false;
    }

    @Override
    public boolean isSkippable() {
        return true;
    }

    @Override
    public StepDefinition getStepDefinition() {
        return StepDefinition.CORPUS;
    }

    @Override
    public StepResult execute(WizardDisplay display, WizardContext context) {
        boolean color = display.isColorSupported();

        display.printLine("Checking services...");
        display.printLine("");

        Map<String, CheckTask> checks = buildChecks(context);
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(checks.size(), 5));

        int online = 0;
        int total = checks.size();
        int row = 2; // start after header lines

        for (Map.Entry<String, CheckTask> entry : checks.entrySet()) {
            String label = entry.getKey();
            CheckTask task = entry.getValue();

            // Show spinner for this service
            WizardDisplay.SpinnerHandle spinner = display.showSpinner(label + "...");

            // Run check
            CompletableFuture<HealthResult> future = CompletableFuture.supplyAsync(
                    () -> task.checker().check(task.config()), executor);

            try {
                HealthResult result = future.get(12, TimeUnit.SECONDS);
                context.addServiceCheckResult(label, result);

                String statusText = mapStatus(result.status());
                StatusColor sColor = mapColor(result.status());
                String detail = result.responseTimeMs() > 0 ? result.responseTimeMs() + "ms" : null;

                spinner.stop(formatResult(label, statusText, detail, sColor, display.getContentWidth(), color));
                if (result.isOnline()) online++;
            } catch (Exception e) {
                HealthResult timeout = HealthResult.offline("timeout (12s)");
                context.addServiceCheckResult(label, timeout);
                spinner.stop(formatResult(label, "DEFICIT", "timeout", StatusColor.BRIGHT,
                        display.getContentWidth(), color));
            }

            // Brief pause between reveals (200ms)
            try { Thread.sleep(200); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }

        executor.shutdown();

        display.printLine("");

        // Summary line
        String summaryTag;
        StatusColor summaryColor;
        if (online == total) {
            summaryTag = "[CORPUS INTEGRUM]";
            summaryColor = StatusColor.OK;
        } else {
            int alerts = total - online;
            summaryTag = "[" + alerts + " ALERT" + (alerts > 1 ? "S" : "") + "]";
            summaryColor = StatusColor.WARN;
        }
        String summaryValue = online + "/" + total + " OPERATIONAL";
        display.statusLine(summaryValue, summaryTag, null, summaryColor);

        display.printLine("");
        display.waitForKey();

        return StepResult.success(online + "/" + total + " services operational.");
    }

    private String formatResult(String label, String status, String detail,
                                StatusColor sColor, int width, boolean color) {
        return org.arcos.Setup.UI.StatusLineRenderer.render(
                label, status, detail, sColor, width, color);
    }

    private String mapStatus(ServiceStatus status) {
        return switch (status) {
            case ONLINE -> "ONLINE";
            case DEGRADED -> "DEGRADED";
            default -> "DEFICIT";
        };
    }

    private StatusColor mapColor(ServiceStatus status) {
        return switch (status) {
            case ONLINE -> StatusColor.OK;
            case DEGRADED -> StatusColor.WARN;
            default -> StatusColor.BRIGHT;
        };
    }

    private Map<String, CheckTask> buildChecks(WizardContext context) {
        Map<String, CheckTask> checks = new LinkedHashMap<>();

        checks.put("QDRANT", new CheckTask(
                new QdrantHealthChecker(),
                ServiceHealthCheck.ServiceConfig.of("localhost", 6334)));

        String mistralKey = context.getModel().getMistralApiKey();
        checks.put("MISTRAL AI", new CheckTask(
                new MistralHealthChecker(),
                ServiceHealthCheck.ServiceConfig.withKey(mistralKey)));

        checks.put("FASTER-WHISPER", new CheckTask(
                new FasterWhisperHealthChecker(),
                ServiceHealthCheck.ServiceConfig.of("localhost", 9876)));

        checks.put("PIPER TTS", new CheckTask(
                new PiperHealthChecker(),
                ServiceHealthCheck.ServiceConfig.of(null, -1)));

        checks.put("PORCUPINE", new CheckTask(
                new PorcupineHealthChecker(),
                ServiceHealthCheck.ServiceConfig.withKey(context.getModel().getPorcupineAccessKey())));

        return checks;
    }

    private record CheckTask(ServiceHealthCheck checker, ServiceHealthCheck.ServiceConfig config) {}
}
