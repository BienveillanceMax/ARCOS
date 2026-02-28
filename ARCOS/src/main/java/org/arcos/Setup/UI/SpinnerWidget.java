package org.arcos.Setup.UI;

import java.io.PrintWriter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Spinner braille animé pour les opérations longues dans le wizard.
 * Met à jour l'affichage toutes les 80ms sur la ligne courante.
 * Thread-safe : start() / stop() peuvent être appelés depuis n'importe quel thread.
 */
public class SpinnerWidget implements AutoCloseable {

    private static final char[] FRAMES = {'⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏'};
    private static final int INTERVAL_MS = 80;

    private final PrintWriter writer;
    private final String label;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> task;
    private volatile int frame = 0;

    public SpinnerWidget(PrintWriter writer, String label) {
        this.writer = writer;
        this.label = label;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "spinner");
            t.setDaemon(true);
            return t;
        });
    }

    /** Démarre l'animation du spinner. */
    public void start() {
        task = scheduler.scheduleAtFixedRate(this::tick, 0, INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void tick() {
        char spinChar = FRAMES[frame % FRAMES.length];
        frame++;
        writer.print("\r" + AnsiPalette.AMBER + spinChar + AnsiPalette.RESET + "  " + label + "  ");
        writer.flush();
    }

    /**
     * Arrête l'animation et remplace par le message de résultat.
     *
     * @param resultLine ligne finale à afficher (ex: "✓ Connecté" ou "✗ Inaccessible")
     */
    public void stop(String resultLine) {
        if (task != null) {
            task.cancel(false);
        }
        writer.print("\r" + resultLine + "\n");
        writer.flush();
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
