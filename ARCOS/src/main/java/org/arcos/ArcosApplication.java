package org.arcos;

import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import org.arcos.Setup.UI.AsciiBanner;
import org.arcos.IO.OuputHandling.StateHandler.CentralFeedBackHandler;
import org.arcos.IO.OuputHandling.StateHandler.UXEventType;
import org.arcos.IO.OuputHandling.StateHandler.FeedBackEvent;
import org.arcos.Orchestrator.Orchestrator;
import org.arcos.Setup.UI.BannerPhase;
import org.arcos.Setup.UI.CogitoPhase;
import org.arcos.Setup.UI.ScreenHolder;
import org.arcos.Setup.UI.TerminalCapabilities;
import org.arcos.Setup.UI.WelcomePhase;
import org.arcos.Setup.UI.WelcomeResult;
import org.arcos.Setup.WizardRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

@Slf4j
@SpringBootApplication(exclude = {
    org.springframework.ai.model.transformers.autoconfigure.TransformersEmbeddingModelAutoConfiguration.class,
    org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingAutoConfiguration.class
})
@EnableScheduling
public class ArcosApplication {

    // Captured before any logger initialization to guarantee clean redirection
    private static final PrintStream ORIGINAL_OUT = System.out;
    private static final PrintStream ORIGINAL_ERR = System.err;

    public static void main(String[] args) {
        // Shutdown hook: ensures terminal is restored on SIGINT (Ctrl+C)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.setOut(ORIGINAL_OUT);
            System.setErr(ORIGINAL_ERR);
            stopScreenIfActive();
            resetTerminalAttributes();
        }, "arcos-screen-cleanup"));

        Screen screen = null;
        try {
            if (TerminalCapabilities.isFullScreenSupported()) {
                // Redirect stdout/stderr BEFORE creating screen or any logger.
                // Lanterna gets the original stream explicitly; loggers get /dev/null.
                PrintStream suppressed = new PrintStream(OutputStream.nullOutputStream());
                System.setOut(suppressed);
                System.setErr(suppressed);

                screen = createLanternaScreen();
            }

            if (screen != null) {
                ScreenHolder.set(screen);
                Thread waitingThread = null;

                // Disable DevTools restart — it conflicts with Lanterna screen lifecycle
                // (SilentExitException would stop and recreate the screen, causing double-boot)
                System.setProperty("spring.devtools.restart.enabled", "false");

                try {
                    // Banner animation
                    BannerPhase.render(screen);

                    // Welcome screen — always shown, replaces --setup/--reconfigure
                    WelcomeResult result = WelcomePhase.show(screen);

                    if (result == WelcomeResult.RECONFIGURE) {
                        WizardRunner.runWizard(screen);
                    }

                    // Launch sequence with subsystem probes while Spring loads
                    waitingThread = CogitoPhase.render(screen);

                    ConfigurableApplicationContext context = SpringApplication.run(ArcosApplication.class, args);

                    // Stop waiting animation
                    waitingThread.interrupt();
                    try { waitingThread.join(500); } catch (InterruptedException ignored) {}

                    // BootReporter.onApplicationReady() renders boot report + greeting into screen,
                    // then stops the screen and clears ScreenHolder.

                    // Restore streams and terminal after screen is closed
                    System.setOut(ORIGINAL_OUT);
                    System.setErr(ORIGINAL_ERR);
                    resetTerminalAttributes();

                    startOrchestrator(context);
                } catch (Exception e) {
                    // Stop spinner thread if running
                    if (waitingThread != null) {
                        waitingThread.interrupt();
                        try { waitingThread.join(500); } catch (InterruptedException ignored) {}
                    }
                    System.setOut(ORIGINAL_OUT);
                    System.setErr(ORIGINAL_ERR);
                    throw e;
                }

            } else {
                // Fallback: no full-screen support
                AsciiBanner.print();
                WizardRunner.runIfNeededFallback(args);

                ConfigurableApplicationContext context = SpringApplication.run(ArcosApplication.class, args);
                startOrchestrator(context);
            }

        } catch (Exception e) {
            // Stop screen FIRST so error is visible in the restored terminal,
            // not lost in the alternate buffer
            System.setOut(ORIGINAL_OUT);
            System.setErr(ORIGINAL_ERR);
            stopScreenIfActive();
            resetTerminalAttributes();
            log.error("Fatal error during startup: {}", e.getMessage(), e);
        } finally {
            System.setOut(ORIGINAL_OUT);
            System.setErr(ORIGINAL_ERR);
            stopScreenIfActive();
            resetTerminalAttributes();
        }
    }

    private static Screen createLanternaScreen() {
        try {
            // Use ORIGINAL_OUT (not System.out which is now suppressed)
            DefaultTerminalFactory factory = new DefaultTerminalFactory(ORIGINAL_OUT, System.in, java.nio.charset.Charset.defaultCharset());
            factory.setForceTextTerminal(false);
            Terminal terminal = factory.createTerminal();
            Screen screen = new TerminalScreen(terminal);
            screen.startScreen();
            screen.setCursorPosition(null);
            return screen;
        } catch (IOException e) {
            log.warn("Cannot create Lanterna terminal, falling back: {}", e.getMessage());
            return null;
        }
    }

    private static void stopScreenIfActive() {
        Screen s = ScreenHolder.get();
        if (s != null) {
            try { s.stopScreen(); } catch (IOException ignored) {}
            ScreenHolder.clear();
        }
    }

    /**
     * Belt-and-suspenders terminal reset via stty.
     * Lanterna's stopScreen() should restore attributes, but if the process
     * is killed abruptly (Ctrl+C, Spring crash), stty ensures echo and
     * canonical mode are re-enabled so the user can type in their shell.
     */
    private static void resetTerminalAttributes() {
        try {
            new ProcessBuilder("/bin/stty", "sane")
                    .redirectInput(ProcessBuilder.Redirect.INHERIT)
                    .start()
                    .waitFor();
        } catch (Exception ignored) {}
    }

    private static void startOrchestrator(ConfigurableApplicationContext context) {
        Orchestrator orchestrator = context.getBean(Orchestrator.class);
        CentralFeedBackHandler handler = context.getBean(CentralFeedBackHandler.class);
        handler.handleFeedBack(new FeedBackEvent(UXEventType.ARCOS_START));
        orchestrator.start();
    }
}
