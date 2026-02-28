package org.arcos.Setup.Steps;

import org.arcos.Setup.Health.FasterWhisperHealthChecker;
import org.arcos.Setup.Health.HealthResult;
import org.arcos.Setup.Health.MistralHealthChecker;
import org.arcos.Setup.Health.PiperHealthChecker;
import org.arcos.Setup.Health.PorcupineHealthChecker;
import org.arcos.Setup.Health.QdrantHealthChecker;
import org.arcos.Setup.Health.ServiceHealthCheck;
import org.arcos.Setup.Health.ServiceStatus;
import org.arcos.Setup.UI.AnsiPalette;
import org.arcos.Setup.UI.TerminalCapabilities;
import org.arcos.Setup.WizardContext;
import org.arcos.Setup.WizardStep;
import org.jline.terminal.Terminal;

import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Étape 4 du wizard : vérification de la connectivité des services externes.
 * Exécute les health checks en parallèle avec affichage des résultats.
 */
public class ServiceCheckStep implements WizardStep {

    @Override
    public String getName() {
        return "Vérification des services";
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
    public StepResult execute(Terminal terminal, WizardContext context) {
        PrintWriter out = terminal.writer();
        boolean color = TerminalCapabilities.isColorSupported();

        printHeader(out, color);

        // Construction des checks selon la config collectée
        Map<String, CheckTask> checks = buildChecks(context);

        // Exécution parallèle avec affichage
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(checks.size(), 5));
        Map<String, CompletableFuture<HealthResult>> futures = new LinkedHashMap<>();

        for (Map.Entry<String, CheckTask> entry : checks.entrySet()) {
            String label = entry.getKey();
            CheckTask task = entry.getValue();
            out.println("  ⟳ " + label + "...");
            CompletableFuture<HealthResult> future = CompletableFuture.supplyAsync(
                    () -> task.checker().check(task.config()), executor);
            futures.put(label, future);
        }

        // Attendre et afficher les résultats
        int online = 0;
        int total = futures.size();

        for (Map.Entry<String, CompletableFuture<HealthResult>> entry : futures.entrySet()) {
            String label = entry.getKey();
            try {
                HealthResult result = entry.getValue().get(12, TimeUnit.SECONDS);
                context.addServiceCheckResult(label, result);
                printResult(out, label, result, color);
                if (result.isOnline()) online++;
            } catch (Exception e) {
                HealthResult timeout = HealthResult.offline("Délai dépassé (12s)");
                context.addServiceCheckResult(label, timeout);
                printResult(out, label, timeout, color);
            }
        }

        executor.shutdown();

        out.println();
        printSummary(out, online, total, color);

        return StepResult.success(online + "/" + total + " services accessibles.");
    }

    private Map<String, CheckTask> buildChecks(WizardContext context) {
        Map<String, CheckTask> checks = new LinkedHashMap<>();

        // Qdrant (toujours)
        checks.put("Qdrant (localhost:6334)", new CheckTask(
                new QdrantHealthChecker(),
                ServiceHealthCheck.ServiceConfig.of("localhost", 6334)));

        // Mistral AI (si clé présente)
        String mistralKey = context.getModel().getMistralApiKey();
        checks.put("Mistral AI", new CheckTask(
                new MistralHealthChecker(),
                ServiceHealthCheck.ServiceConfig.withKey(mistralKey)));

        // faster-whisper (toujours)
        checks.put("faster-whisper (localhost:9876)", new CheckTask(
                new FasterWhisperHealthChecker(),
                ServiceHealthCheck.ServiceConfig.of("localhost", 9876)));

        // Piper TTS (toujours)
        checks.put("Piper TTS", new CheckTask(
                new PiperHealthChecker(),
                ServiceHealthCheck.ServiceConfig.of(null, -1)));

        // Porcupine (si clé présente)
        String porcupineKey = context.getModel().getPorcupineAccessKey();
        checks.put("Porcupine (wake word)", new CheckTask(
                new PorcupineHealthChecker(),
                ServiceHealthCheck.ServiceConfig.withKey(porcupineKey)));

        return checks;
    }

    private void printHeader(PrintWriter out, boolean color) {
        String cyan = color ? AnsiPalette.CYAN : "";
        String bold = color ? AnsiPalette.BOLD : "";
        String reset = color ? AnsiPalette.RESET : "";
        out.println();
        out.println(cyan + bold + "── ÉTAPE 4 : Vérification des services ──────────────────" + reset);
        out.println("  Vérification de la connectivité des services externes...");
        out.println();
    }

    private void printResult(PrintWriter out, String label, HealthResult result, boolean color) {
        String icon;
        String statusColor;
        String reset = color ? AnsiPalette.RESET : "";

        if (result.status() == ServiceStatus.ONLINE) {
            icon = "✓";
            statusColor = color ? AnsiPalette.GREEN : "";
        } else if (result.status() == ServiceStatus.DEGRADED) {
            icon = "⚠";
            statusColor = color ? AnsiPalette.YELLOW : "";
        } else {
            icon = "✗";
            statusColor = color ? AnsiPalette.RED : "";
        }

        String suffix = result.responseTimeMs() > 0
                ? " (" + result.responseTimeMs() + "ms)"
                : "";
        out.println("  " + statusColor + icon + reset + " " + label +
                " — " + result.message() + suffix);
    }

    private void printSummary(PrintWriter out, int online, int total, boolean color) {
        String statusColor = online == total
                ? (color ? AnsiPalette.GREEN : "")
                : (color ? AnsiPalette.YELLOW : "");
        String reset = color ? AnsiPalette.RESET : "";

        String tag = online == total ? "[ALL SYSTEMS GO]" : "[" + (total - online) + " WARNING(S)]";
        out.println("  " + statusColor + online + "/" + total + " services accessibles " + tag + reset);
    }

    private record CheckTask(ServiceHealthCheck checker, ServiceHealthCheck.ServiceConfig config) {}
}
