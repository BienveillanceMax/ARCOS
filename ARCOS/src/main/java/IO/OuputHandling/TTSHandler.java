package IO.OuputHandling;

import java.util.List;


//Wrapper class
//A local installation of piper is necessary for the program to work /!\

public class TTSHandler
{
    PiperEmbeddedTTSModule piperEmbeddedTTSModule;

    public TTSHandler() {
        List<PiperEmbeddedTTSModule.EmbeddedModel> available = PiperEmbeddedTTSModule.getAvailableModels();
        System.out.println("Modèles intégrés trouvés: " + available.size());
        if (available.isEmpty()) {
            System.err.println("Aucun modèle trouvé dans les ressources!");
            System.err.println("Assurez-vous d'avoir inclus les fichiers .onnx dans src/main/resources/models/");
            return;
        }

        this.piperEmbeddedTTSModule = new PiperEmbeddedTTSModule(PiperEmbeddedTTSModule.EmbeddedModel.UPMC);
    }

    public void initialize() {
        this.piperEmbeddedTTSModule.initialize();
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

