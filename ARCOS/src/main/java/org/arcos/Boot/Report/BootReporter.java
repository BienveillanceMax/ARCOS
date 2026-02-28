package org.arcos.Boot.Report;

import org.arcos.Boot.Greeting.PersonalityGreeting;
import org.arcos.Boot.ServiceStatus;
import org.arcos.Boot.ServiceStatusEntry;
import org.arcos.Boot.ServiceStatusRegistry;
import org.arcos.Configuration.PersonalityProperties;
import org.arcos.Configuration.QdrantProperties;
import org.arcos.Tools.SearchTool.BraveSearchService;
import org.arcos.Setup.UI.TerminalCapabilities;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Déclenche l'affichage du rapport de boot après démarrage complet de Spring.
 * Collecte les statuts des services et délègue le rendu à BootReportRenderer.
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

    @Value("${MISTRALAI_API_KEY:}")
    private String mistralApiKey;

    @Value("${PORCUPINE_ACCESS_KEY:}")
    private String porcupineKey;

    @Value("${BRAVE_SEARCH_API_KEY:}")
    private String braveKey;

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
                        BraveSearchService braveSearchService) {
        this.registry = registry;
        this.greeting = greeting;
        this.personalityProperties = personalityProperties;
        this.braveSearchService = braveSearchService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        collectServiceStatuses();
        renderReport();
        printGreeting();
    }

    private void collectServiceStatuses() {
        // ── PERSONNALITÉ ──────────────────────────────────────────────────────
        registry.register("Profil", ServiceStatus.ONLINE,
                personalityProperties.getProfile(), CATEGORY_PERSONALITY);

        // ── SYSTÈMES CŒUR ─────────────────────────────────────────────────────
        // LLM
        if (mistralApiKey != null && !mistralApiKey.isBlank()) {
            registry.register("LLM", ServiceStatus.ONLINE,
                    "Mistral (" + mistralModel + ")", CATEGORY_CORE);
        } else {
            registry.register("LLM", ServiceStatus.OFFLINE,
                    "MISTRALAI_API_KEY absent", CATEGORY_CORE);
        }

        // Qdrant — si Spring a démarré, Qdrant est forcément connecté
        registry.register("VECTOR DB", ServiceStatus.ONLINE,
                "Qdrant (" + qdrantHost + ":" + qdrantPort + ")", CATEGORY_CORE);

        // TTS Piper
        ServiceStatusEntry piperStatus = checkPiperStatus();
        registry.register(piperStatus.getName(), piperStatus.getStatus(),
                piperStatus.getDetail(), CATEGORY_CORE);

        // ── INTERACTION ───────────────────────────────────────────────────────
        // Wake word
        if (porcupineKey != null && !porcupineKey.isBlank()) {
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

    private ServiceStatusEntry checkPiperStatus() {
        String piperDir = System.getProperty("user.home") + "/.piper-tts";
        File piperDirFile = new File(piperDir);
        if (!piperDirFile.exists()) {
            return new ServiceStatusEntry("TTS", ServiceStatus.OFFLINE,
                    "Piper introuvable dans ~/.piper-tts", CATEGORY_CORE);
        }
        File piperExe = findPiperExecutable(piperDirFile);
        if (piperExe == null) {
            return new ServiceStatusEntry("TTS", ServiceStatus.OFFLINE,
                    "Exécutable piper absent", CATEGORY_CORE);
        }
        return new ServiceStatusEntry("TTS", ServiceStatus.ONLINE,
                "Piper TTS prêt", CATEGORY_CORE);
    }

    private File findPiperExecutable(File dir) {
        if (!dir.exists()) return null;
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.isFile() && f.getName().equals("piper") && f.canExecute()) return f;
            if (f.isDirectory()) {
                File found = findPiperExecutable(f);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void renderReport() {
        boolean colorOn = TerminalCapabilities.isColorSupported();
        BootReportRenderer renderer = new BootReportRenderer();
        renderer.render(registry.getAll(), colorOn);
    }

    private void printGreeting() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println(greeting.generateMessage(registry.hasIssues()));
        System.out.flush();
    }
}
