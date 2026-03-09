package org.arcos.Setup.UI;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import org.arcos.Setup.Detection.ConfigurationDetector;
import org.arcos.Setup.Health.FasterWhisperHealthChecker;
import org.arcos.Setup.Health.HealthResult;
import org.arcos.Setup.Health.MistralHealthChecker;
import org.arcos.Setup.Health.PiperHealthChecker;
import org.arcos.Setup.Health.QdrantHealthChecker;
import org.arcos.Setup.Health.ServiceHealthCheck;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * COGITO phase: dramatic launch sequence with real subsystem health probes.
 * Runs while Spring loads. Shows "HIC SUNT DRACONES" title card, then
 * subsystems initializing one by one with spinners and scramble-decode results.
 * Returns a decoration thread that loops until interrupted (when Spring finishes).
 *
 * Ctrl+C terminates the JVM. Escape skips remaining probes.
 */
public final class CogitoPhase {

    private CogitoPhase() {}

    private static final String DECORATION_GLYPHS = "░▓█▒0123456789ABCDEF";
    private static final int PROBE_TIMEOUT_SECONDS = 12;

    /**
     * Renders the full COGITO launch sequence into the screen.
     *
     * @return the decoration thread (interrupt it when Spring finishes)
     */
    public static Thread render(Screen screen) {
        TextGraphics tg = screen.newTextGraphics();
        LanternaPalette palette = LanternaPalette.DEFAULT;
        LayoutCalculator.ScreenLayout layout = LayoutCalculator.calculate(screen.getTerminalSize());
        ReentrantLock lock = new ReentrantLock();

        // 1. Clear interior
        lock.lock();
        try {
            for (int r = layout.headerRow() + 1; r < layout.footerRow(); r++) {
                LanternaComponents.drawEmptyRow(tg, layout, r, palette);
            }
            screen.refresh();
        } catch (IOException ignored) {
        } finally {
            lock.unlock();
        }
        Animations.sleepWithPoll(screen, 50);

        // 2. Draw "LAUNCH SEQUENCE" panel divider
        lock.lock();
        try {
            LanternaComponents.drawPanelDivider(tg, layout, "", "LAUNCH SEQUENCE", palette);
            screen.refresh();
        } catch (IOException ignored) {
        } finally {
            lock.unlock();
        }
        Animations.sleepWithPoll(screen, 100);

        // 3. Scramble-decode "HIC SUNT DRACONES" centered in panel (with Ctrl+C polling)
        String title = "HIC SUNT DRACONES";
        int titleX = layout.leftMargin() + (layout.frameWidth() - title.length()) / 2;
        int titleRow = layout.panelContentStart();
        Animations.scrambleDecode(tg, screen, titleX, titleRow,
                title, palette.dim(), palette.bright(), 500, lock,
                () -> { pollInput(screen); return false; });

        // 4. Hold 400ms (follow-through — let the title breathe)
        Animations.sleepWithPoll(screen, 400);

        // 5. Resolve config for probes
        String mistralKey = resolveEnv("MISTRALAI_API_KEY", null);
        String qdrantHost = resolveEnv("QDRANT_HOST", "localhost");
        int qdrantPort = resolveEnvInt("QDRANT_PORT", 6334);
        String whisperUrl = resolveEnv("FASTER_WHISPER_URL", null);
        String whisperHost;
        int whisperPort;
        if (whisperUrl != null && !whisperUrl.isBlank()) {
            String stripped = whisperUrl.replaceFirst("^https?://", "");
            String[] parts = stripped.split(":");
            whisperHost = parts[0];
            whisperPort = parts.length > 1 ? parseIntOrDefault(parts[1].replaceAll("/.*", ""), 9876) : 9876;
        } else {
            whisperHost = "localhost";
            whisperPort = 9876;
        }

        // If no Mistral key from env, try .env file
        if (mistralKey == null || mistralKey.isBlank()) {
            try {
                var config = new ConfigurationDetector().loadExistingConfiguration();
                if (config.getMistralApiKey() != null && !config.getMistralApiKey().isBlank()) {
                    mistralKey = config.getMistralApiKey();
                }
            } catch (Exception ignored) {}
        }

        // 6. Subsystem probes — sequential, each with typewriter + spinner + resolve
        int currentRow = titleRow + 2;
        boolean skipped = false;

        SubsystemProbe[] subsystems = {
            new SubsystemProbe("LANGUAGE MODEL", new MistralHealthChecker(),
                    ServiceHealthCheck.ServiceConfig.withKey(mistralKey), true),
            new SubsystemProbe("LONG-TERM MEMORY", new QdrantHealthChecker(),
                    ServiceHealthCheck.ServiceConfig.of(qdrantHost, qdrantPort), true),
            new SubsystemProbe("SPEECH RECOGNITION", new FasterWhisperHealthChecker(),
                    ServiceHealthCheck.ServiceConfig.of(whisperHost, whisperPort), true),
            new SubsystemProbe("SPEECH SYNTHESIS", new PiperHealthChecker(),
                    ServiceHealthCheck.ServiceConfig.of(null, -1), false),
            new SubsystemProbe("PERSONALITY ENGINE", null, null, false),
        };

        for (SubsystemProbe sub : subsystems) {
            if (skipped) {
                // Fast-resolve remaining subsystems as SKIPPED
                renderSkipped(tg, screen, layout, palette, lock, currentRow, sub.name);
            } else {
                InputAction action = renderSubsystem(tg, screen, layout, palette, lock, currentRow, sub);
                if (action == InputAction.SKIP) {
                    skipped = true;
                }
            }
            currentRow++;
            if (!skipped) Animations.sleepWithPoll(screen, 200);
        }

        // 7. Start and return scramble decoration loop
        int decoRow = currentRow + 1;
        int decoLen = 26;
        int decoX = layout.leftMargin() + (layout.frameWidth() - decoLen) / 2;

        return Thread.ofVirtual().name("cogito-decoration").start(() -> {
            Random rng = new Random();
            while (!Thread.currentThread().isInterrupted()) {
                lock.lock();
                try {
                    StringBuilder deco = new StringBuilder();
                    for (int i = 0; i < decoLen; i++) {
                        deco.append(DECORATION_GLYPHS.charAt(rng.nextInt(DECORATION_GLYPHS.length())));
                    }
                    tg.setForegroundColor(palette.dim());
                    tg.putString(decoX, decoRow, deco.toString());
                    screen.refresh();
                } catch (IOException ignored) {
                } finally {
                    lock.unlock();
                }
                try {
                    Thread.sleep(80);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    private enum InputAction { NONE, SKIP }

    /**
     * Renders a single subsystem probe row with typewriter + dot-leader, then resolves status.
     * The probe runs in parallel with the dot animation — no spinner.
     */
    private static InputAction renderSubsystem(TextGraphics tg, Screen screen,
                                                LayoutCalculator.ScreenLayout layout,
                                                LanternaPalette palette, ReentrantLock lock,
                                                int row, SubsystemProbe sub) {
        int leftX = layout.leftMargin() + 4;

        // a. Start probe early so it runs during the typewriter + dot animation
        CompletableFuture<HealthResult> future;
        if (sub.checker == null) {
            future = CompletableFuture.completedFuture(HealthResult.online("Profile configured", 0));
        } else {
            future = CompletableFuture.supplyAsync(
                    () -> sub.checker.check(sub.config),
                    runnable -> Thread.ofVirtual().name("probe-" + sub.name).start(runnable)
            ).orTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        // b. Typewriter name
        Animations.typewriter(tg, screen, leftX, row, sub.name, palette.text(), 30, lock);

        // c. Dot-leader (character by character)
        int contentW = layout.contentWidth();
        String statusText = "OPERATIONAL";
        int dotsNeeded = contentW - sub.name.length() - statusText.length() - 2 - 8;
        if (dotsNeeded < 3) dotsNeeded = 3;

        int dotStartX = leftX + sub.name.length() + 1;
        InputAction action = InputAction.NONE;
        for (int d = 0; d < dotsNeeded; d++) {
            InputAction polled = pollInput(screen);
            if (polled == InputAction.SKIP) {
                future.cancel(true);
                action = InputAction.SKIP;
                break;
            }
            lock.lock();
            try {
                tg.setForegroundColor(palette.muted());
                tg.putString(dotStartX + d, row, ".");
                screen.refresh();
            } catch (IOException ignored) {
            } finally {
                lock.unlock();
            }
            Animations.sleep(20);
        }

        if (action == InputAction.SKIP) {
            int statusX = dotStartX + dotsNeeded + 1;
            drawStatus(tg, screen, layout, palette, lock, row, statusX, "SKIPPED", palette.muted(), null);
            return action;
        }

        int statusX = dotStartX + dotsNeeded + 1;

        // d. Wait silently for probe if it hasn't finished yet (no spinner)
        while (!future.isDone()) {
            InputAction polled = pollInput(screen);
            if (polled == InputAction.SKIP) {
                future.cancel(true);
                drawStatus(tg, screen, layout, palette, lock, row, statusX, "SKIPPED", palette.muted(), null);
                return InputAction.SKIP;
            }
            Animations.sleep(50);
        }

        // e. Get result
        HealthResult result;
        try {
            result = future.get(1, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            result = HealthResult.offline("Timeout");
        } catch (Exception e) {
            result = HealthResult.offline("Probe error");
        }

        // e. Resolve status display
        boolean online = result.isOnline();
        String displayStatus = online ? "OPERATIONAL" : "OFFLINE";
        TextColor statusColor = online ? palette.ok() : palette.bright();
        String timing = (sub.isNetworkProbe && result.responseTimeMs() > 0)
                ? "  " + result.responseTimeMs() + "ms" : null;

        drawStatus(tg, screen, layout, palette, lock, row, statusX, displayStatus, statusColor, timing);

        return action;
    }

    private static void drawStatus(TextGraphics tg, Screen screen,
                                    LayoutCalculator.ScreenLayout layout,
                                    LanternaPalette palette, ReentrantLock lock,
                                    int row, int statusX,
                                    String displayStatus, TextColor statusColor,
                                    String timing) {
        // Clear spinner position
        lock.lock();
        try {
            int clearLen = layout.leftMargin() + layout.frameWidth() - 1 - statusX;
            if (clearLen > 0) {
                tg.putString(statusX, row, " ".repeat(clearLen));
            }
            screen.refresh();
        } catch (IOException ignored) {
        } finally {
            lock.unlock();
        }

        boolean isOperational = "OPERATIONAL".equals(displayStatus);
        if (isOperational) {
            Animations.scrambleDecode(tg, screen, statusX, row,
                    displayStatus, palette.dim(), statusColor, 300, lock,
                    () -> { pollInput(screen); return false; });
        } else {
            lock.lock();
            try {
                tg.setForegroundColor(statusColor);
                tg.putString(statusX, row, displayStatus);
                screen.refresh();
            } catch (IOException ignored) {
            } finally {
                lock.unlock();
            }
        }

        // Response time
        if (timing != null) {
            lock.lock();
            try {
                tg.setForegroundColor(palette.muted());
                tg.putString(statusX + displayStatus.length(), row, timing);
                screen.refresh();
            } catch (IOException ignored) {
            } finally {
                lock.unlock();
            }
        }

        // Ensure right border
        lock.lock();
        try {
            tg.setForegroundColor(palette.primary());
            tg.putString(layout.leftMargin() + layout.frameWidth() - 1, row, "┃");
            screen.refresh();
        } catch (IOException ignored) {
        } finally {
            lock.unlock();
        }
    }

    /**
     * Fast-render a skipped subsystem row (no animation, just instant draw).
     */
    private static void renderSkipped(TextGraphics tg, Screen screen,
                                       LayoutCalculator.ScreenLayout layout,
                                       LanternaPalette palette, ReentrantLock lock,
                                       int row, String name) {
        int leftX = layout.leftMargin() + 4;
        int contentW = layout.contentWidth();
        String statusText = "SKIPPED";
        int dotsNeeded = contentW - name.length() - statusText.length() - 2 - 8;
        if (dotsNeeded < 3) dotsNeeded = 3;
        int dotStartX = leftX + name.length() + 1;
        int statusX = dotStartX + dotsNeeded + 1;

        lock.lock();
        try {
            // Name
            tg.setForegroundColor(palette.text());
            tg.putString(leftX, row, name);
            // Dots
            tg.setForegroundColor(palette.muted());
            tg.putString(dotStartX, row, ".".repeat(dotsNeeded));
            // Status
            tg.setForegroundColor(palette.muted());
            tg.putString(statusX, row, statusText);
            // Right border
            tg.setForegroundColor(palette.primary());
            tg.putString(layout.leftMargin() + layout.frameWidth() - 1, row, "┃");
            screen.refresh();
        } catch (IOException ignored) {
        } finally {
            lock.unlock();
        }
    }

    /**
     * Non-blocking keyboard poll. Ctrl+C (char 3) exits the JVM.
     * Escape returns SKIP to skip remaining probes.
     */
    private static InputAction pollInput(Screen screen) {
        try {
            KeyStroke key = screen.pollInput();
            if (key == null) return InputAction.NONE;

            if (key.getKeyType() == KeyType.Character && key.getCharacter() == '\u0003') {
                // Ctrl+C — terminate
                System.exit(130);
            }
            if (key.getKeyType() == KeyType.Escape) {
                return InputAction.SKIP;
            }
        } catch (IOException ignored) {
        }
        return InputAction.NONE;
    }

    private static String resolveEnv(String key, String defaultValue) {
        String val = System.getenv(key);
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }

    private static int resolveEnvInt(String key, int defaultValue) {
        String val = System.getenv(key);
        if (val != null && !val.isBlank()) {
            return parseIntOrDefault(val, defaultValue);
        }
        return defaultValue;
    }

    private static int parseIntOrDefault(String s, int defaultValue) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private record SubsystemProbe(String name, ServiceHealthCheck checker,
                                   ServiceHealthCheck.ServiceConfig config,
                                   boolean isNetworkProbe) {}
}
