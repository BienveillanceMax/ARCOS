package org.arcos.Setup.Boot;

import com.googlecode.lanterna.screen.Screen;
import org.arcos.Configuration.PersonalityProperties;
import org.arcos.Configuration.SpeechToTextProperties;
import org.arcos.IO.InputHandling.STT.SttBackendType;
import org.arcos.Setup.Health.HealthResult;
import org.arcos.Setup.Health.PiperHealthChecker;
import org.arcos.Setup.Health.QdrantHealthChecker;
import org.arcos.Setup.Health.ServiceHealthCheck;
import org.arcos.Setup.Health.ServiceStatus;
import org.arcos.Setup.Health.SttHealthChecker;
import org.arcos.Tools.CalendarTool.CalDavCalendarService;
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
import java.net.URI;
import java.util.concurrent.TimeUnit;

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
    private final SpeechToTextProperties sttProperties;
    private final BraveSearchService braveSearchService;
    private final CalDavCalendarService calendarService;
    private final ApplicationContext applicationContext;

    private ServiceHealthCheck qdrantChecker = new QdrantHealthChecker();
    private ServiceHealthCheck sttChecker = new SttHealthChecker();

    public void setHealthCheckers(ServiceHealthCheck qdrant, ServiceHealthCheck stt) { // test seam
        this.qdrantChecker = qdrant;
        this.sttChecker = stt;
    }

    @Value("${qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${qdrant.port:6334}")
    private int qdrantPort;

    @Value("${spring.ai.mistralai.chat.options.model:mistral-large-2512}")
    private String mistralModel;

    public BootReporter(ServiceStatusRegistry registry,
                        PersonalityGreeting greeting,
                        PersonalityProperties personalityProperties,
                        SpeechToTextProperties sttProperties,
                        BraveSearchService braveSearchService,
                        CalDavCalendarService calendarService,
                        ApplicationContext applicationContext) {
        this.registry = registry;
        this.greeting = greeting;
        this.personalityProperties = personalityProperties;
        this.sttProperties = sttProperties;
        this.braveSearchService = braveSearchService;
        this.calendarService = calendarService;
        this.applicationContext = applicationContext;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    public void onApplicationReady() {
        collectServiceStatuses();
        renderReport();
    }

    public void collectServiceStatuses() {
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

        // Qdrant — sonde réelle (TCP), pas de statut fabriqué
        HealthResult qdrant = qdrantChecker.check(ServiceHealthCheck.ServiceConfig.of(qdrantHost, qdrantPort));
        registry.register("VECTOR DB", qdrant.status(),
                qdrant.isOnline() ? "Qdrant (" + qdrantHost + ":" + qdrantPort + ")" : qdrant.message(),
                CATEGORY_CORE);

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

        // Speech-to-Text — sonde réelle sur l'URL du backend sélectionné
        String sttUrl = switch (sttProperties.getBackend()) {
            case FASTER_WHISPER -> sttProperties.getFasterWhisperUrl();
            case WHISPER_CPP -> sttProperties.getWhisperCppUrl();
        };
        URI u = URI.create(sttUrl);
        int sttPort = u.getPort() > 0 ? u.getPort()
                : (sttProperties.getBackend() == SttBackendType.WHISPER_CPP ? 8090 : 8000);
        HealthResult stt = sttChecker.check(
                ServiceHealthCheck.ServiceConfig.of(u.getHost() != null ? u.getHost() : "localhost", sttPort));
        String sttLabel = switch (sttProperties.getBackend()) {
            case FASTER_WHISPER -> "Faster Whisper (" + sttUrl + ")";
            case WHISPER_CPP -> "Whisper.cpp (" + sttUrl + ")";
        };
        registry.register("VOIX", stt.status(),
                stt.isOnline() ? sttLabel : sttLabel + " — " + stt.message(),
                CATEGORY_INTERACTION);

        // ── OUTILS ────────────────────────────────────────────────────────────
        // Web search
        if (braveSearchService.isAvailable()) {
            registry.register("RECHERCHE WEB", ServiceStatus.ONLINE,
                    "Brave Search", CATEGORY_TOOLS);
        } else {
            registry.register("RECHERCHE WEB", ServiceStatus.OFFLINE,
                    "BRAVE_SEARCH_API_KEY absent", CATEGORY_TOOLS);
        }

        // Calendar (Radicale CalDAV)
        if (calendarService.isAvailable()) {
            registry.register("CALENDRIER", ServiceStatus.ONLINE,
                    "Radicale (localhost:5232)", CATEGORY_TOOLS);
        } else {
            registry.register("CALENDRIER", ServiceStatus.OFFLINE,
                    "Radicale non disponible", CATEGORY_TOOLS);
        }

        // Tailscale (VPN mesh pour accès distant)
        ServiceStatusEntry tailscaleStatus = checkTailscaleStatus();
        registry.register(tailscaleStatus.getName(), tailscaleStatus.getStatus(),
                tailscaleStatus.getDetail(), CATEGORY_TOOLS);
    }

    private boolean hasBeanOfType(Class<?> type) {
        try {
            return !applicationContext.getBeansOfType(type).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private ServiceStatusEntry checkTailscaleStatus() {
        try {
            ProcessBuilder pb = new ProcessBuilder("tailscale", "ip", "-4");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ServiceStatusEntry("TAILSCALE", ServiceStatus.OFFLINE,
                        "timeout", CATEGORY_TOOLS);
            }
            int exitCode = process.exitValue();
            if (exitCode == 0 && !output.isBlank()) {
                return new ServiceStatusEntry("TAILSCALE", ServiceStatus.ONLINE,
                        output, CATEGORY_TOOLS);
            }
            return new ServiceStatusEntry("TAILSCALE", ServiceStatus.OFFLINE,
                    "non connecté", CATEGORY_TOOLS);
        } catch (Exception e) {
            return new ServiceStatusEntry("TAILSCALE", ServiceStatus.OFFLINE,
                    "non installé", CATEGORY_TOOLS);
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
