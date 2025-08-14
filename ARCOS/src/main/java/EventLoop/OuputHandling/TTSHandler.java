package EventLoop.OuputHandling;

import OrchestratorV2.Entities.EventType;
import OrchestratorV2.OrchestratorV2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;


//Wrapper class
//A local installation of piper is necessary for the program to work /!\
@Component
public class TTSHandler {
    private final OrchestratorV2 orchestratorV2;
    private PiperEmbeddedTTSModule piperEmbeddedTTSModule;

    @Autowired
    public TTSHandler(OrchestratorV2 orchestratorV2) {
        this.orchestratorV2 = orchestratorV2;
    }

    @PostConstruct
    public void initialize() {
        List<PiperEmbeddedTTSModule.EmbeddedModel> available = PiperEmbeddedTTSModule.getAvailableModels();
        System.out.println("Modèles intégrés trouvés: " + available.size());
        if (available.isEmpty()) {
            System.err.println("Aucun modèle trouvé dans les ressources!");
            System.err.println("Assurez-vous d'avoir inclus les fichiers .onnx dans src/main/resources/models/");
            return;
        }

        this.piperEmbeddedTTSModule = new PiperEmbeddedTTSModule(PiperEmbeddedTTSModule.EmbeddedModel.UPMC);
        this.piperEmbeddedTTSModule.initialize();

        orchestratorV2.getEventStream()
                .filter(event -> event.getType() == EventType.ASSISTANT_RESPONSE_GENERATED)
                .subscribe(event -> speak((String) event.getPayload()));
    }

    public void speak(String message) {
        this.piperEmbeddedTTSModule.speak(message);
    }

    public void stop() {
        this.piperEmbeddedTTSModule.stop();
    }

    public void start() {
        if (!piperEmbeddedTTSModule.initialize()) {
            System.err.println("Impossible d'initialiser le module TTS");
        }
    }

    public void shutdown() {
        this.piperEmbeddedTTSModule.shutdown();
    }
}

