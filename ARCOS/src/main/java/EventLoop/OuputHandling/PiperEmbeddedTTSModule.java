package EventLoop.OuputHandling;


import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sound.sampled.*;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Module TTS Piper avec modèles intégrés dans les ressources
 * Les modèles sont extraits automatiquement au premier lancement
 */
public class PiperEmbeddedTTSModule
{

    private final BlockingQueue<TTSRequest> requestQueue;
    private final ExecutorService executorService;
    private final AtomicBoolean isRunning;
    private final AtomicBoolean isInitialized;

    // Chemins des ressources
    private final String piperExecutablePath;
    private String extractedModelPath;

    // Configuration audio
    private AudioFormat audioFormat;
    private SourceDataLine audioLine;

    // Paramètres Piper
    private float speechRate = 1.0f;
    private int sampleRate = 22050;
    private float noiseScale = 0.667f;
    private float noiseScaleW = 0.8f;

    // Cache et répertoires temporaires
    private final File tempDirectory;
    private final File modelsDirectory;
    private final ConcurrentHashMap<String, File> audioCache;

    // Modèles disponibles dans les ressources
    public enum EmbeddedModel
    {
        //SIWIS("models/fr_FR-siwis-medium.onnx", "models/fr_FR-siwis-medium.onnx.json", "Voix masculine claire"),
        //TOM("models/fr_FR-tom-medium.onnx", "models/fr_FR-tom-medium.onnx.json", "Voix masculine rapide"),
        UPMC("upmc-model/fr_FR-upmc-medium.onnx", "upmc-model/fr_FR-upmc-medium.onnx.json", "Voix masculine naturelle");

        private final String modelResource;
        private final String configResource;
        private final String description;

        EmbeddedModel(String modelResource, String configResource, String description) {
            this.modelResource = modelResource;
            this.configResource = configResource;
            this.description = description;
        }

        public String getModelResource() {
            return modelResource;
        }

        public String getConfigResource() {
            return configResource;
        }

        public String getDescription() {
            return description;
        }

        public String getModelFileName() {
            return Paths.get(modelResource).getFileName().toString();
        }

        public String getConfigFileName() {
            return Paths.get(configResource).getFileName().toString();
        }
    }

    public PiperEmbeddedTTSModule() {
        this(findPiperExecutable(), EmbeddedModel.UPMC);
    }

    public PiperEmbeddedTTSModule(EmbeddedModel preferredModel) {
        this(findPiperExecutable(), preferredModel);
    }

    public PiperEmbeddedTTSModule(String piperExecutablePath, EmbeddedModel model) {
        this.piperExecutablePath = piperExecutablePath;
        this.requestQueue = new LinkedBlockingQueue<>();
        this.executorService = Executors.newFixedThreadPool(3);
        this.isRunning = new AtomicBoolean(false);
        this.isInitialized = new AtomicBoolean(false);
        this.audioCache = new ConcurrentHashMap<>();

        try {
            // Création des répertoires temporaires
            this.tempDirectory = Files.createTempDirectory("piper_tts").toFile();
            this.modelsDirectory = new File(tempDirectory, "models");
            this.modelsDirectory.mkdirs();

            // Extraction du modèle préféré
            this.extractedModelPath = extractModel(model);

            // Nettoyage à la fermeture de la JVM
            Runtime.getRuntime().addShutdownHook(new Thread(this::cleanup));

        } catch (IOException e) {
            throw new RuntimeException("Impossible de créer les répertoires temporaires", e);
        }
    }

    /**
     * Extrait un modèle des ressources vers le système de fichiers
     */
    private String extractModel(EmbeddedModel model) throws IOException {
        System.out.println("Extraction du modèle " + model.name() + "...");

        // Extraction du modèle ONNX
        File modelFile = new File(modelsDirectory, model.getModelFileName());
        extractResource(model.getModelResource(), modelFile);

        // Extraction de la configuration JSON
        File configFile = new File(modelsDirectory, model.getConfigFileName());
        extractResource(model.getConfigResource(), configFile);

        System.out.println("Modèle extrait vers: " + modelFile.getAbsolutePath());
        return modelFile.getAbsolutePath();
    }

    /**
     * Extrait une ressource vers un fichier
     */
    private void extractResource(String resourcePath, File targetFile) throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Ressource non trouvée: " + resourcePath);
            }

            Files.copy(inputStream, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Trouve l'exécutable Piper dans le système
     */
    private static String findPiperExecutable() {
        String[] possiblePaths = {
                "/usr/local/bin/piper",
                "/usr/bin/piper",
                "./piper",
                "piper.exe",
                "C:\\piper\\piper.exe",
                System.getProperty("user.home") + "/piper/piper"
        };

        for (String path : possiblePaths) {
            if (Files.exists(Paths.get(path))) {
                return path;
            }
        }
        //System.out.println();
        return "piper"; // Par défaut dans le PATH
    }

    /**
     * Initialise le module TTS
     */
    public boolean initialize() {
        try {
            System.out.println(Paths.get(piperExecutablePath));
            // Vérification de l'exécutable Piper
            if (!Files.exists(Paths.get(piperExecutablePath))) {
                System.err.println("Exécutable Piper non trouvé: " + piperExecutablePath);
                System.err.println("Installez Piper ou spécifiez le chemin correct.");
                return false;
            }

            // Vérification du modèle extrait
            if (!Files.exists(Paths.get(extractedModelPath))) {
                System.err.println("Modèle extrait non trouvé: " + extractedModelPath);
                return false;
            }

            // Test de Piper
            if (!testPiperConnection()) {
                return false;
            }

            // Configuration audio
            setupAudioSystem();

            isInitialized.set(true);
            startProcessing();

            System.out.println("Module TTS Piper avec modèles intégrés initialisé avec succès");
            System.out.println("Modèle utilisé: " + extractedModelPath);

            return true;

        } catch (Exception e) {
            System.err.println("Erreur lors de l'initialisation: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Test de connexion avec Piper
     */
    private boolean testPiperConnection() {

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    piperExecutablePath,
                    "--model", extractedModelPath,
                    "--output_file", "/dev/null"
            );

            Process process = pb.start();

            // Write input and close the stream properly
            try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream(), "UTF-8")) {
                writer.write("Test de connexion.");
                writer.flush();
            } // Stream is automatically closed here due to try-with-resources

            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                System.err.println("Timeout lors du test Piper");
                return false;
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                System.out.println("Test Piper réussi avec modèle intégré");
                return true;
            } else {
                System.err.println("Erreur Piper, code de sortie: " + exitCode);

                // Read both stdout and stderr for better diagnosis
                try (BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                     BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

                    String line;
                    System.err.println("--- STDOUT ---");
                    while ((line = stdoutReader.readLine()) != null) {
                        System.err.println("Piper stdout: " + line);
                    }

                    System.err.println("--- STDERR ---");
                    while ((line = stderrReader.readLine()) != null) {
                        System.err.println("Piper stderr: " + line);
                    }
                }
                return false;
            }

        } catch (Exception e) {
            System.err.println("Erreur lors du test Piper: " + e.getMessage());
            e.printStackTrace(); // Add stack trace for better debugging
            return false;
        }
    }

    private void debugAudioSystem() {
        System.out.println("=== DEBUG AUDIO SYSTEM ===");

        // Liste tous les mixers disponibles
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        System.out.println("Mixers audio disponibles:");
        for (int i = 0; i < mixers.length; i++) {
            System.out.println(i + ": " + mixers[i].getName() + " - " + mixers[i].getDescription());
        }

        // Affiche le mixer utilisé par défaut
        try {
            Mixer defaultMixer = AudioSystem.getMixer(null);
            System.out.println("Mixer par défaut: " + defaultMixer.getMixerInfo().getName());

            // Vérifie les lignes disponibles
            Line.Info[] sourceLines = defaultMixer.getSourceLineInfo();
            System.out.println("Lignes de sortie disponibles: " + sourceLines.length);
            for (Line.Info lineInfo : sourceLines) {
                System.out.println("- " + lineInfo);
            }

        } catch (Exception e) {
            System.err.println("Erreur lors du debug audio: " + e.getMessage());
        }
    }

    private AudioFormat findSupportedAudioFormat(Mixer mixer) {
        // Formats à essayer par ordre de préférence
        AudioFormat[] formatsToTry = {
                // Format original
                new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 22050, 16, 1, 2, 22050, false),
                // Formats alternatifs
                new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 1, 2, 44100, false), // 44.1kHz mono
                new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 2, 4, 44100, false), // 44.1kHz stéréo
                new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 48000, 16, 1, 2, 48000, false), // 48kHz mono
                new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 48000, 16, 2, 4, 48000, false), // 48kHz stéréo
                new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 22050, 16, 2, 4, 22050, false), // 22kHz stéréo
        };

        for (AudioFormat format : formatsToTry) {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

            try {
                if (mixer != null) {
                    if (mixer.isLineSupported(info)) {
                        System.out.println("Format supporté trouvé: " + format);
                        return format;
                    }
                } else {
                    if (AudioSystem.isLineSupported(info)) {
                        System.out.println("Format supporté trouvé (défaut): " + format);
                        return format;
                    }
                }
            } catch (Exception e) {
                // Continue à essayer
            }
        }

        System.err.println("Aucun format audio supporté trouvé!");
        return null;
    }

    private Mixer findBestAudioMixer() {
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();

        // Priorité 1: Chercher un mixer avec "Generic" et "Analog" (sortie casque/haut-parleurs)
        for (Mixer.Info mixerInfo : mixers) {
            String name = mixerInfo.getName().toLowerCase();
            String desc = mixerInfo.getDescription().toLowerCase();

            if ((name.contains("generic") || desc.contains("analog")) &&
                    !name.contains("nvidia") && !name.contains("hdmi")) {

                try {
                    Mixer mixer = AudioSystem.getMixer(mixerInfo);
                    DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);

                    if (mixer.isLineSupported(info)) {
                        System.out.println("Mixer trouvé (priorité 1): " + mixerInfo.getName());
                        return mixer;
                    }
                } catch (Exception e) {
                    // Continue à chercher
                }
            }
        }
        // Priorité 2: Chercher un mixer qui n'est pas HDMI/NVidia
        for (Mixer.Info mixerInfo : mixers) {
            String name = mixerInfo.getName().toLowerCase();

            if (!name.contains("nvidia") && !name.contains("hdmi") && !name.contains("port")) {
                try {
                    Mixer mixer = AudioSystem.getMixer(mixerInfo);
                    DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);

                    if (mixer.isLineSupported(info)) {
                        System.out.println("Mixer trouvé (priorité 2): " + mixerInfo.getName());
                        return mixer;
                    }
                } catch (Exception e) {
                    // Continue à chercher
                }
            }
        }
        System.out.println("Aucun mixer spécifique trouvé, utilisation du défaut");
        return null; // Utiliser le mixer par défaut
    }

    /**
     * Configuration du système audio
     */

    /**
     * Configuration du système audio avec diagnostic complet
     */
    private void setupAudioSystem() throws LineUnavailableException {
        // Version simplifiée - pas besoin de configurer une ligne persistante
        // Chaque fichier audio créera sa propre SourceDataLine
        System.out.println("Configuration audio simplifiée - utilisation du mixer par défaut");

        // Test rapide pour vérifier que l'audio fonctionne
        AudioFormat testFormat = new AudioFormat(44100, 16, 2, true, false);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, testFormat);

        if (AudioSystem.isLineSupported(info)) {
            System.out.println("✓ Support audio confirmé: " + testFormat);
        } else {
            throw new LineUnavailableException("Format audio de base non supporté");
        }
    }
    /**
     * Démarre le traitement des requêtes
     */
    private void startProcessing() {
        if (isRunning.compareAndSet(false, true)) {
            executorService.submit(this::processRequests);
        }
    }

    /**
     * Traite les demandes TTS
     */
    private void processRequests() {
        while (isRunning.get()) {
            try {
                TTSRequest request = requestQueue.take();
                if (request.isShutdownSignal()) {
                    break;
                }

                long startTime = System.currentTimeMillis();

                // Synthèse avec Piper
                File audioFile = synthesizeWithPiper(request.getText());

                // Lecture audio
                if (audioFile != null && audioFile.exists()) {
                    playAudioFile(audioFile);

                    // Nettoyage du fichier temporaire
                    audioFile.delete();
                }

                long endTime = System.currentTimeMillis();
                System.out.println("TTS traité en " + (endTime - startTime) + "ms");

                request.markCompleted();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Erreur lors du traitement: " + e.getMessage());
            }
        }
    }

    /**
     * Synthétise le texte avec Piper
     */
    private File synthesizeWithPiper(String text) {
        try {
            File outputFile = File.createTempFile("piper_audio_", ".wav", tempDirectory);

            List<String> command = new ArrayList<>();
            command.add(piperExecutablePath);
            command.add("--model");
            command.add(extractedModelPath);
            command.add("--output_file");
            command.add(outputFile.getAbsolutePath());
            command.add("--length_scale");
            command.add(String.valueOf(1.0f / speechRate));
            command.add("--noise_scale");
            command.add(String.valueOf(noiseScale));
            command.add("--noise_scale_w");
            command.add(String.valueOf(noiseScaleW));

            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();

            try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream(), "UTF-8")) {
                writer.write(preprocessText(text));
                writer.flush();
            }

            boolean finished = process.waitFor(15, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }
            if (outputFile.exists()) {
                System.out.println("Fichier audio généré: " + outputFile.length() + " bytes");
            } else {
                System.err.println("Aucun fichier audio généré");
            }

            return process.exitValue() == 0 && outputFile.exists() ? outputFile : null;

        } catch (Exception e) {
            System.err.println("Erreur lors de la synthèse: " + e.getMessage());
            return null;
        }
    }

    /**
     * Prétraite le texte
     */
    private String preprocessText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        return text.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("([.!?])([A-Z])", "$1 $2");
    }


    /**
     * Lit un fichier audio
     */
    /**
     * Lit un fichier audio avec conversion de format si nécessaire
     */
    private void playAudioFile(File audioFile) {
        System.out.println("=== LECTURE AUDIO SIMPLIFIÉE ===");
        System.out.println("Fichier: " + audioFile.getAbsolutePath());
        System.out.println("Taille: " + audioFile.length() + " bytes");

        try (AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile)) {

            AudioFormat audioFormat = audioStream.getFormat();
            System.out.println("Format du fichier: " + audioFormat);

            // Créer DataLine.Info pour SourceDataLine (pas Clip)
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);

            // Obtenir et configurer la ligne audio
            SourceDataLine sourceDataLine = (SourceDataLine) AudioSystem.getLine(info);
            sourceDataLine.open(audioFormat);
            sourceDataLine.start();

            System.out.println("Format de la ligne: " + sourceDataLine.getFormat());
            System.out.println("Début de la lecture...");

            // Buffer pour la lecture par chunks
            byte[] buffer = new byte[4096];
            int bytesRead;
            int totalBytesPlayed = 0;
            long startTime = System.currentTimeMillis();

            // Lire et jouer l'audio chunk par chunk
            while ((bytesRead = audioStream.read(buffer)) != -1) {
                sourceDataLine.write(buffer, 0, bytesRead);
                totalBytesPlayed += bytesRead;

                // Debug périodique
                if (totalBytesPlayed % (buffer.length * 10) == 0) {
                    System.out.println("Lu: " + totalBytesPlayed + " bytes");
                }
            }

            // Attendre que tout l'audio soit joué
            sourceDataLine.drain();
            sourceDataLine.close();

            long endTime = System.currentTimeMillis();
            System.out.println("Audio terminé. Total: " + totalBytesPlayed + " bytes en " + (endTime - startTime) + "ms");

        } catch (Exception e) {
            System.err.println("Erreur lors de la lecture audio: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Change de modèle à la volée
     */
    public boolean switchModel(EmbeddedModel newModel) {
        try {
            // Extraction du nouveau modèle
            String newModelPath = extractModel(newModel);

            // Test du nouveau modèle
            String oldPath = this.extractedModelPath;
            this.extractedModelPath = newModelPath;

            if (testPiperConnection()) {
                System.out.println("Modèle changé vers: " + newModel.getDescription());

                // Nettoyage de l'ancien modèle
                try {
                    Files.deleteIfExists(Paths.get(oldPath));
                    Files.deleteIfExists(Paths.get(oldPath.replace(".onnx", ".onnx.json")));
                } catch (IOException e) {
                    // Ignore les erreurs de nettoyage
                }

                return true;
            } else {
                // Restauration de l'ancien modèle
                this.extractedModelPath = oldPath;
                return false;
            }

        } catch (Exception e) {
            System.err.println("Erreur lors du changement de modèle: " + e.getMessage());
            return false;
        }
    }

    /**
     * Liste les modèles intégrés disponibles
     */
    public static List<EmbeddedModel> getAvailableModels() {
        List<EmbeddedModel> available = new ArrayList<>();

        for (EmbeddedModel model : EmbeddedModel.values()) {
            // Vérification de la présence dans les ressources
            InputStream stream = PiperEmbeddedTTSModule.class.getClassLoader()
                    .getResourceAsStream(model.getModelResource());
            if (stream != null) {
                try {
                    stream.close();
                    available.add(model);
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
        return available;
    }

    /**
     * Synthèse asynchrone
     */
    public Future<Void> speakAsync(String text) {
        if (!isInitialized.get()) {
            throw new IllegalStateException("Module TTS non initialisé");
        }

        TTSRequest request = new TTSRequest(text);
        requestQueue.offer(request);

        return executorService.submit(() -> {
            request.waitForCompletion();
            return null;
        });
    }

    /**
     * Synthèse synchrone
     */
    public void speak(String text) {
        try {
            speakAsync(text).get();
        } catch (Exception e) {
            System.err.println("Erreur lors de la synthèse: " + e.getMessage());
        }
    }

    /**
     * Configure la vitesse de parole
     */
    public void setSpeechRate(float rate) {
        this.speechRate = Math.max(0.5f, Math.min(2.0f, rate));
    }

    /**
     * Configure les paramètres de bruit
     */
    public void setNoiseParameters(float noiseScale, float noiseScaleW) {
        this.noiseScale = Math.max(0.0f, Math.min(1.0f, noiseScale));
        this.noiseScaleW = Math.max(0.0f, Math.min(1.0f, noiseScaleW));
    }

    /**
     * Arrête la synthèse
     */
    public void stop() {
        requestQueue.clear();
        if (audioLine != null) {
            audioLine.flush();
        }
    }

    /**
     * Ferme le module
     */
    public void shutdown() {
        isRunning.set(false);
        requestQueue.offer(new TTSRequest(null, true));

        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }

        if (audioLine != null) {
            audioLine.drain();
            audioLine.close();
        }

        cleanup();
        System.out.println("Module TTS fermé");
    }

    /**
     * Nettoyage des fichiers temporaires
     */
    private void cleanup() {
        if (tempDirectory != null && tempDirectory.exists()) {
            try {
                Files.walk(tempDirectory.toPath())
                        .sorted((a, b) -> b.compareTo(a))
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                // Ignore
                            }
                        });
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    public boolean isInitialized() {
        return isInitialized.get();
    }

    public int getQueueSize() {
        return requestQueue.size();
    }

    public void startSpeakandStop(String text) {
        List<EmbeddedModel> available = getAvailableModels();
        System.out.println("Modèles intégrés trouvés: " + available.size());
        this.initialize();
        this.speak(text);
        //this.shutdown();
    }

    /**
     * Classe interne pour les requêtes TTS
     */
    private static class TTSRequest
    {
        private final String text;
        private final boolean shutdownSignal;
        private final Object completionLock = new Object();
        private volatile boolean completed = false;

        public TTSRequest(String text) {
            this(text, false);
        }

        public TTSRequest(String text, boolean shutdownSignal) {
            this.text = text;
            this.shutdownSignal = shutdownSignal;
        }

        public String getText() {
            return text;
        }

        public boolean isShutdownSignal() {
            return shutdownSignal;
        }

        public void markCompleted() {
            synchronized (completionLock) {
                completed = true;
                completionLock.notifyAll();
            }
        }

        public void waitForCompletion() {
            synchronized (completionLock) {
                while (!completed) {
                    try {
                        completionLock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    /**
     * Exemple d'utilisation
     */
    public static void main(String[] args) {
        System.out.println("=== Module TTS Piper avec modèles intégrés ===");

        // Vérification des modèles disponibles
        List<EmbeddedModel> available = getAvailableModels();
        System.out.println("Modèles intégrés trouvés: " + available.size());

        if (available.isEmpty()) {
            System.err.println("Aucun modèle trouvé dans les ressources!");
            System.err.println("Assurez-vous d'avoir inclus les fichiers .onnx dans src/main/resources/models/");
            return;
        }

        // Utilisation du premier modèle disponible
        EmbeddedModel selectedModel = available.get(0);
        System.out.println("Utilisation du modèle: " + selectedModel.getDescription());

        PiperEmbeddedTTSModule tts = new PiperEmbeddedTTSModule(selectedModel);

        if (!tts.initialize()) {
            System.err.println("Impossible d'initialiser le module TTS");
            return;
        }

        try {
            // Tests
            tts.speak("Bonjour, je suis un module TTS avec modèles intégrés.");
            tts.speak("Les modèles sont extraits automatiquement des ressources Java.");

            // Test de changement de modèle
            if (available.size() > 1) {
                System.out.println("Test de changement de modèle...");
                if (tts.switchModel(available.get(1))) {
                    tts.speak("Nouveau modèle de voix activé avec succès.");
                }
            }

            // Configuration
            tts.setSpeechRate(1.2f);
            tts.speak("Voici un test avec une vitesse plus rapide.");

            System.out.println("Tests terminés avec succès!");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            tts.shutdown();
        }
    }
}