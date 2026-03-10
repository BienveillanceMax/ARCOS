package org.arcos.Setup.Boot;

import com.googlecode.lanterna.screen.Screen;
import org.arcos.Configuration.PersonalityProperties;
import org.arcos.Setup.Health.PiperHealthChecker;
import org.arcos.Setup.Health.ServiceStatus;
import org.arcos.Tools.SearchTool.BraveSearchService;
import org.arcos.Setup.UI.BootPhaseRenderer;
import org.arcos.Setup.UI.ScreenHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Déclenche l'affichage du rapport de boot après démarrage complet de Spring.
 * Collecte les statuts des services et délègue le rendu à BootPhaseRenderer.
 */
@Component
@Slf4j
public class BootReporter {

    private static final String CATEGORY_CORE = "SYSTÈMES CŒUR";
    private static final String CATEGORY_INTERACTION = "INTERACTION";
    private static final String CATEGORY_TOOLS = "OUTILS";
    private static final String CATEGORY_PERSONALITY = "PERSONNALITÉ";

    private final ServiceStatusRegistry registry;
    private final PersonalityGreeting greeting;
    private final PersonalityProperties personalityProperties;
    private final BraveSearchService braveSearchService;
    private final ApplicationContext applicationContext;

    @Value("${qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${qdrant.port:6334}")
    private int qdrantPort;

    @Value("${faster-whisper.url:http://localhost:9876}")
    private String fasterWhisperUrl;

    @Value("${spring.ai.mistralai.chat.options.model:mistral-large-2512}")
    private String mistralModel;

    public BootReporter(ServiceStatusRegistry registry,
                        PersonalityGreeting greeting,
                        PersonalityProperties personalityProperties,
                        BraveSearchService braveSearchService,
                        ApplicationContext applicationContext) {
        this.registry = registry;
        this.greeting = greeting;
        this.personalityProperties = personalityProperties;
        this.braveSearchService = braveSearchService;
        this.applicationContext = applicationContext;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    public void onApplicationReady() {
        collectServiceStatuses();
        renderReport();
    }

    private void collectServiceStatuses() {
        // ── PERSONNALITÉ ──────────────────────────────────────────────────────
        registry.register("Profil", ServiceStatus.ONLINE,
                personalityProperties.getProfile(), CATEGORY_PERSONALITY);

        // ── SYSTÈMES CŒUR ─────────────────────────────────────────────────────
        // LLM — if Spring started and created a ChatModel bean, the LLM is online
        if (hasBeanOfType(ChatModel.class)) {
            registry.register("LLM", ServiceStatus.ONLINE,
                    "Mistral (" + mistralModel + ")", CATEGORY_CORE);
        } else {
            registry.register("LLM", ServiceStatus.OFFLINE,
                    "No ChatModel bean", CATEGORY_CORE);
        }

        // Qdrant — si Spring a démarré, Qdrant est forcément connecté
        registry.register("VECTOR DB", ServiceStatus.ONLINE,
                "Qdrant (" + qdrantHost + ":" + qdrantPort + ")", CATEGORY_CORE);

        // TTS Piper
        ServiceStatusEntry piperStatus = checkPiperStatus();
        registry.register(piperStatus.getName(), piperStatus.getStatus(),
                piperStatus.getDetail(), CATEGORY_CORE);

        // ── INTERACTION ───────────────────────────────────────────────────────
        // Wake word — check via bean presence (WakeWordProducer uses Porcupine)
        boolean porcupineAvailable = applicationContext.getEnvironment()
                .getProperty("PORCUPINE_ACCESS_KEY", "").length() > 0;
        if (porcupineAvailable) {
            registry.register("MOT DE RÉVEIL", ServiceStatus.ONLINE,
                    "Porcupine actif", CATEGORY_INTERACTION);
        } else {
            registry.register("MOT DE RÉVEIL", ServiceStatus.OFFLINE,
                    "PORCUPINE_ACCESS_KEY absent", CATEGORY_INTERACTION);
        }

        // Speech-to-Text
        registry.register("VOIX", ServiceStatus.ONLINE,
                "Faster Whisper (" + fasterWhisperUrl + ")", CATEGORY_INTERACTION);

        // ── OUTILS ────────────────────────────────────────────────────────────
        // Web search
        if (braveSearchService.isAvailable()) {
            registry.register("RECHERCHE WEB", ServiceStatus.ONLINE,
                    "Brave Search", CATEGORY_TOOLS);
        } else {
            registry.register("RECHERCHE WEB", ServiceStatus.OFFLINE,
                    "BRAVE_SEARCH_API_KEY absent", CATEGORY_TOOLS);
        }
    }

    private boolean hasBeanOfType(Class<?> type) {
        try {
            return !applicationContext.getBeansOfType(type).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private ServiceStatusEntry checkPiperStatus() {
        var result = new PiperHealthChecker().check(null);
        ServiceStatus status = result.isOnline() ? ServiceStatus.ONLINE : ServiceStatus.OFFLINE;
        String detail = result.isOnline() ? "Piper TTS prêt" : result.message();
        return new ServiceStatusEntry("TTS", status, detail, CATEGORY_CORE);
    }

    private void renderReport() {
        String greetingMessage = greeting.generateMessage(registry.hasIssues());
        Screen screen = ScreenHolder.get();
        if (screen != null) {
            BootPhaseRenderer phaseRenderer = new BootPhaseRenderer(screen);
            phaseRenderer.render(registry.getAll(), greetingMessage);

            // Stop screen after greeting keypress
            try {
                screen.stopScreen();
            } catch (IOException ignored) {}
            ScreenHolder.clear();
        } else {
            // Fallback: simple summary + greeting to stdout
            long online = registry.getAll().stream().filter(ServiceStatusEntry::isOnline).count();
            int total = registry.getAll().size();
            System.out.println(online + "/" + total + " systems online");
            System.out.println(greetingMessage);
            System.out.flush();
        }
    }
}
