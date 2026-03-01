package org.arcos.Setup.UI;

import java.io.PrintWriter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Geometric spinner for async operations.
 * Frames: ◒ ◐ ◓ ◑ at 100ms interval, in PRIMARY color.
 * Two modes:
 * - Legacy: writes to PrintWriter with \r-based line replacement
 * - Screen-integrated: writes via a position callback (for ScreenManager)
 */
public class SpinnerWidget implements AutoCloseable {

    private static final char[] FRAMES = {'◒', '◐', '◓', '◑'};
    private static final int INTERVAL_MS = 100;

    private final ScheduledExecutorService scheduler;
    private final Runnable tickAction;
    private ScheduledFuture<?> task;
    private volatile int frame = 0;

    // Legacy fields
    private final PrintWriter writer;
    private final String label;

    /**
     * Legacy constructor: \r-based spinner on a PrintWriter.
     */
    public SpinnerWidget(PrintWriter writer, String label) {
        this.writer = writer;
        this.label = label;
        this.tickAction = null;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "spinner");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Screen-integrated constructor: delegates rendering to a callback.
     *
     * @param writeCallback receives the formatted spinner text each tick
     */
    public SpinnerWidget(Consumer<String> writeCallback, String label) {
        this.writer = null;
        this.label = label;
        this.tickAction = () -> {
            char spinChar = FRAMES[frame % FRAMES.length];
            frame++;
            String text = AnsiPalette.PRIMARY + spinChar + AnsiPalette.RESET + "  " + label;
            writeCallback.accept(text);
        };
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "spinner");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (tickAction != null) {
            task = scheduler.scheduleAtFixedRate(tickAction, 0, INTERVAL_MS, TimeUnit.MILLISECONDS);
        } else {
            task = scheduler.scheduleAtFixedRate(this::legacyTick, 0, INTERVAL_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void legacyTick() {
        char spinChar = FRAMES[frame % FRAMES.length];
        frame++;
        writer.print("\r" + AnsiPalette.PRIMARY + spinChar + AnsiPalette.RESET + "  " + label + "  ");
        writer.flush();
    }

    public void stop(String resultLine) {
        if (task != null) {
            task.cancel(false);
        }
        if (writer != null) {
            writer.print("\r" + resultLine + "\n");
            writer.flush();
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
