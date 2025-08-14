package EventLoop.OuputHandling;

import com.arcos.bus.EventBus;
import com.arcos.events.SpeakEvent;

import java.util.List;

public class TTSHandler {
    private final PiperEmbeddedTTSModule piperEmbeddedTTSModule;
    private final EventBus eventBus;

    public TTSHandler() {
        List<PiperEmbeddedTTSModule.EmbeddedModel> available = PiperEmbeddedTTSModule.getAvailableModels();
        System.out.println("Modèles intégrés trouvés: " + available.size());
        if (available.isEmpty()) {
            System.err.println("Aucun modèle trouvé dans les ressources!");
            System.err.println("Assurez-vous d'avoir inclus les fichiers .onnx dans src/main/resources/models/");
            this.piperEmbeddedTTSModule = null; // or handle error appropriately
            this.eventBus = null;
            return;
        }

        this.piperEmbeddedTTSModule = new PiperEmbeddedTTSModule(PiperEmbeddedTTSModule.EmbeddedModel.UPMC);
        this.eventBus = EventBus.getInstance();
        this.eventBus.subscribe(SpeakEvent.class, this::handleSpeakEvent);
    }

    private void handleSpeakEvent(SpeakEvent event) {
        speak(event.getText());
    }

    public void initialize() {
        if (this.piperEmbeddedTTSModule != null) {
            this.piperEmbeddedTTSModule.initialize();
        }
    }

    public void speak(String message) {
        if (this.piperEmbeddedTTSModule != null) {
            this.piperEmbeddedTTSModule.speak(message);
        }
    }

    public void stop() {
        if (this.piperEmbeddedTTSModule != null) {
            this.piperEmbeddedTTSModule.stop();
        }
    }

    public void start() {
        if (piperEmbeddedTTSModule != null && !piperEmbeddedTTSModule.initialize()) {
            System.err.println("Impossible d'initialiser le module TTS");
        }
    }

    public void shutdown() {
        if (this.piperEmbeddedTTSModule != null) {
            this.piperEmbeddedTTSModule.shutdown();
        }
    }
}
