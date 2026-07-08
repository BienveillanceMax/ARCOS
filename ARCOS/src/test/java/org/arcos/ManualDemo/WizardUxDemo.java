package org.arcos.ManualDemo;

import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import org.arcos.Setup.ConfigurationModel;
import org.arcos.Setup.StepDefinition;
import org.arcos.Setup.Steps.PersonalityStep;
import org.arcos.Setup.Steps.SttBackendStep;
import org.arcos.Setup.UI.BannerPhase;
import org.arcos.Setup.UI.LanternaScreenManager;
import org.arcos.Setup.WizardContext;

import java.util.List;

/**
 * Manual visual demo of the wizard TUI — run inside tmux and drive with
 * send-keys. Exercises the network-free interactive surfaces:
 * banner → frame + step index → INTERPRES menu → ANIMA live detail pane
 * → FIAT LUX reveal. Never touches the filesystem or the network.
 */
public final class WizardUxDemo {

    public static void main(String[] args) throws Exception {
        DefaultTerminalFactory factory = new DefaultTerminalFactory();
        Terminal terminal = factory.createTerminal();
        Screen screen = new TerminalScreen(terminal);
        screen.startScreen();
        screen.setCursorPosition(null);

        try {
            // Phase 1 — banner (waits for Enter)
            BannerPhase.render(screen);

            // Phase 2 — wizard frame with the full 6-step index
            LanternaScreenManager display = new LanternaScreenManager(screen);
            display.initializeSteps(List.of(
                    StepDefinition.NEXUS, StepDefinition.VOX,
                    StepDefinition.INTERPRES, StepDefinition.ANIMA,
                    StepDefinition.CORPUS, StepDefinition.FIAT));
            display.drawFrame();
            display.completeStep(0);
            display.completeStep(1);

            WizardContext context = new WizardContext(new ConfigurationModel());

            // Phase 3 — INTERPRES backend menu
            display.activateStep(2);
            new SttBackendStep().execute(display, context);
            display.completeStep(2);

            // Phase 4 — ANIMA profile menu with live detail pane
            display.activateStep(3);
            new PersonalityStep().execute(display, context);
            display.completeStep(3);

            // Phase 5 — FIAT reveal
            display.activateStep(5);
            display.printLine("");
            display.reveal("FIAT LUX.");
            display.setKeyHints("↵ EXIT DEMO");
            display.waitForKey();
        } finally {
            screen.stopScreen();
        }
    }
}
