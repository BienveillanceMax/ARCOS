package IO.OuputHandling;


import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import javax.sound.sampled.*;
import java.util.List;
import java.util.ArrayList;

/**
 * Module TTS Piper avec modèles intégrés dans les ressources
 * Les modèles sont extraits automatiquement au premier lancement
 */
@Slf4j
public class PiperEmbeddedTTSModule
{

    private final BlockingQueue<TTSRequest> requestQueue;
    private final ExecutorService executorService;
    private final AtomicBoolean isRunning;
    private final AtomicBoolean isInitialized;

    // Chemins des ressources
    private final String piperExecutablePath;
    private String extractedModelPath;

    // Paramètres Piper
    private float speechRate = 1.0f;
    private int sampleRate = 22050;
    private float noiseScale = 0.667f;
    private float noiseScaleW = 0.8f;

    // Cache et répertoires temporaires
    private File tempDirectory;
    private File modelsDirectory;
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
        this(null, EmbeddedModel.UPMC);
    }

    public PiperEmbeddedTTSModule(EmbeddedModel preferredModel) {
        this(null, preferredModel);
    }

    public PiperEmbeddedTTSModule(String piperExecutablePath, EmbeddedModel model) {
        this.requestQueue = new LinkedBlockingQueue<>();
        this.executorService = Executors.newFixedThreadPool(3);
        this.isRunning = new AtomicBoolean(false);
        this.isInitialized = new AtomicBoolean(false);
        this.audioCache = new ConcurrentHashMap<>();

        try {
            // 1. Create temporary directory
            this.tempDirectory = Files.createTempDirectory("piper_tts").toFile();

            // 2. Ensure Piper executable is available
            if (piperExecutablePath == null || piperExecutablePath.trim().isEmpty()) {
                this.piperExecutablePath = ensurePiperExecutable();
            } else {
                this.piperExecutablePath = piperExecutablePath;
            }

            // 3. Create models directory
            this.modelsDirectory = new File(tempDirectory, "models");
            if (!this.modelsDirectory.mkdirs() && !this.modelsDirectory.exists()) {
                throw new IOException("Could not create models directory: " + this.modelsDirectory.getAbsolutePath());
            }

            // 4. Extract the preferred model
            this.extractedModelPath = extractModel(model);

            // 5. Add a shutdown hook for cleanup
            Runtime.getRuntime().addShutdownHook(new Thread(this::cleanup));

        } catch (IOException e) {
            log.error("Failed to initialize PiperTTSModule", e);
            // Clean up partially created directories if initialization fails
            cleanup();
            // Fail fast
            throw new RuntimeException("Failed to initialize PiperTTSModule", e);
        }
    }

    /**
     * Extrait un modèle des ressources vers le système de fichiers
     */
    private String extractModel(EmbeddedModel model) throws IOException {
        log.info("Extraction du modèle {}...", model.name());

        // Extraction du modèle ONNX
        File modelFile = new File(modelsDirectory, model.getModelFileName());
        extractResource(model.getModelResource(), modelFile);

        // Extraction de la configuration JSON
        File configFile = new File(modelsDirectory, model.getConfigFileName());
        extractResource(model.getConfigResource(), configFile);

        log.info("Modèle extrait vers: {}", modelFile.getAbsolutePath());
        return modelFile.getAbsolutePath();
    }

    /**
     * Extrait une ressource vers un fichier, en la téléchargeant si elle n'est pas trouvée localement.
     */
    private void extractResource(String resourcePath, File targetFile) throws IOException {
        // Try to find resource in classpath first
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream != null) {
                log.debug("Extracting resource from classpath: {}", resourcePath);
                Files.copy(inputStream, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return;
            }
        } catch (Exception e) {
            log.warn("Could not read resource from classpath: {}", e.getMessage());
        }

        // If not found, attempt to download from Hugging Face
        log.warn("Resource not found in classpath: {}. Attempting to download from Hugging Face.", resourcePath);
        String modelUrl = buildModelUrl(resourcePath);

        if (modelUrl == null) {
            throw new IOException("Could not construct download URL for " + resourcePath);
        }

        try {
            downloadFile(modelUrl, targetFile.toPath());
        } catch (IOException e) {
            log.error("Failed to download model from {}: {}", modelUrl, e.getMessage());
            // Chain the exception
            throw new IOException("Failed to download model for resource: " + resourcePath, e);
        }
    }

    private static String buildModelUrl(String resourcePath) {
        String fileName = Paths.get(resourcePath).getFileName().toString();
        // Assuming filename format like: {locale}-{voice}-{quality}.onnx
        String[] parts = fileName.replace(".onnx.json", "").replace(".onnx", "").split("-");

        if (parts.length < 3) {
            log.error("Cannot determine model URL from resource path format: {}", resourcePath);
            return null;
        }

        String locale = parts[0];
        String voice = parts[1];
        String quality = parts[2];
        String lang = locale.substring(0, 2);

        return String.format(
                "https://huggingface.co/rhasspy/piper-voices/resolve/main/%s/%s/%s/%s/%s",
                lang, locale, voice, quality, fileName);
    }

    private void downloadFile(String urlString, Path targetPath) throws IOException {
        URL url = new URL(urlString);
        log.info("Downloading model from {}", url);

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        // It's good practice to set timeouts
        connection.setConnectTimeout(10000); // 10 seconds
        connection.setReadTimeout(30000);    // 30 seconds

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (InputStream inputStream = connection.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                log.info("Successfully downloaded model to {}", targetPath);
            }
        } else {
            throw new IOException("Failed to download model. Server responded with code: " + responseCode + " for URL: " + urlString);
        }
    }

    private String ensurePiperExecutable() {
        // 1. Check common paths for existing executable
        String[] possiblePaths = {
                "./piper",
                "piper.exe",
                "/usr/local/bin/piper",
                "/usr/bin/piper",
                "C:\\piper\\piper.exe",
                System.getProperty("user.home") + "/piper/piper"
        };

        for (String path : possiblePaths) {
            if (Files.exists(Paths.get(path))) {
                log.info("Found existing piper executable at: {}", path);
                return path;
            }
        }

        // 2. If not found, download it
        log.info("Piper executable not found. Attempting to download...");
        try {
            return downloadPiper();
        } catch (IOException e) {
            log.error("Failed to download piper executable", e);
            throw new RuntimeException("Could not find or download piper executable.", e);
        }
    }

    private String downloadPiper() throws IOException {
        String os = getOS();
        String arch = getArch();
        String version = "2023.11.14-2";
        String fileName;
        String url;

        if ("unknown".equals(os) || "unknown".equals(arch)) {
            throw new IOException("Unsupported OS or architecture: " + os + " " + arch);
        }

        switch (os) {
            case "windows":
                fileName = "piper_windows_amd64.zip";
                break;
            case "linux":
                fileName = "piper_linux_" + (arch.equals("amd64") ? "x86_64" : "aarch64") + ".tar.gz";
                break;
            case "macos":
                fileName = "piper_macos_" + (arch.equals("amd64") ? "x86_64" : "aarch64") + ".zip";
                break;
            default:
                throw new IOException("Unsupported OS: " + os);
        }

        url = "https://github.com/rhasspy/piper/releases/download/" + version + "/" + fileName;

        Path targetPath = Paths.get(tempDirectory.getAbsolutePath(), fileName);
        downloadFile(url, targetPath);

        // Unzip/untar and find the executable
        File piperExe = decompress(targetPath.toFile(), tempDirectory);

        // Make executable
        if (!piperExe.setExecutable(true)) {
            log.warn("Could not make piper executable. This might cause issues on Linux/macOS.");
        }

        return piperExe.getAbsolutePath();
    }

    private File decompress(File sourceFile, File destinationDir) throws IOException {
        String fileName = sourceFile.getName();
        if (fileName.endsWith(".zip")) {
            return unzip(sourceFile, destinationDir);
        } else if (fileName.endsWith(".tar.gz")) {
            return untarGz(sourceFile, destinationDir);
        }
        throw new IOException("Unsupported archive format: " + fileName);
    }

    private File unzip(File zipFile, File destDir) throws IOException {
        byte[] buffer = new byte[1024];
        File piperExecutable = null;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File newFile = new File(destDir, zipEntry.getName());
                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory " + newFile);
                    }
                } else {
                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory " + parent);
                    }
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    if (newFile.getName().startsWith("piper")) {
                        piperExecutable = newFile;
                    }
                }
                zipEntry = zis.getNextEntry();
            }
        }
        if (piperExecutable == null) {
            throw new IOException("Could not find piper executable in zip archive");
        }
        return piperExecutable;
    }

    private File untarGz(File tarGzFile, File destDir) throws IOException {
        File piperExecutable = null;
        try (FileInputStream fis = new FileInputStream(tarGzFile);
             GZIPInputStream gzis = new GZIPInputStream(fis);
             // Standard Java doesn't have a TarInputStream. We'll use a simplified manual extraction.
             // This is a basic implementation that assumes a simple tar structure.
             // For more complex tar files, a library like Apache Commons Compress would be better.
             InputStream is = new BufferedInputStream(gzis)) {

            byte[] buffer = new byte[1024];
            // A TAR archive is a sequence of 512-byte records.
            byte[] headerBuffer = new byte[512];

            while (is.read(headerBuffer) != -1) {
                String name = new String(headerBuffer, 0, 100).trim();
                if (name.isEmpty()) {
                    continue; // End of archive marker
                }
                String sizeStr = new String(headerBuffer, 124, 12).trim();
                long size = Long.parseLong(sizeStr, 8);

                File outputFile = new File(destDir, name);

                if (name.endsWith("/")) { // It's a directory
                    outputFile.mkdirs();
                } else { // It's a file
                    outputFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                        long bytesRemaining = size;
                        while (bytesRemaining > 0) {
                            int read = is.read(buffer, 0, (int) Math.min(buffer.length, bytesRemaining));
                            if (read == -1) break;
                            fos.write(buffer, 0, read);
                            bytesRemaining -= read;
                        }
                    }
                    if (outputFile.getName().startsWith("piper")) {
                        piperExecutable = outputFile;
                    }
                }

                // Skip padding to the next 512-byte boundary
                long padding = (512 - (size % 512)) % 512;
                if (is.skip(padding) != padding) {
                    throw new IOException("Failed to skip padding in tar archive");
                }
            }
        }
        if (piperExecutable == null) {
            throw new IOException("Could not find piper executable in tar.gz archive");
        }
        return piperExecutable;
    }

    private String getOS() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return "windows";
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            return "linux";
        } else if (osName.contains("mac")) {
            return "macos";
        }
        return "unknown";
    }

    private String getArch() {
        String osArch = System.getProperty("os.arch").toLowerCase();
        if (osArch.contains("amd64") || osArch.contains("x86_64")) {
            return "amd64";
        } else if (osArch.contains("aarch64") || osArch.contains("arm64")) {
            return "aarch64";
        }
        return "unknown";
    }

    /**
     * Initialise le module TTS
     */
    public boolean initialize() {
        if (isInitialized.get()) {
            log.warn("Module is already initialized.");
            return true;
        }

        try {
            // The constructor now ensures the executable and model are ready.
            log.info("Initializing Piper TTS Module...");
            log.info("Using piper executable at: {}", piperExecutablePath);
            log.info("Using model: {}", extractedModelPath);

            // 1. Test Piper connection
            if (!testPiperConnection()) {
                log.error("Piper connection test failed. Initialization aborted.");
                return false;
            }

            // 2. Configure audio system
            setupAudioSystem();

            // 3. Start processing thread
            startProcessing();

            isInitialized.set(true);
            log.info("Piper TTS Module initialized successfully.");
            return true;

        } catch (Exception e) {
            log.error("A critical error occurred during initialization", e);
            // Ensure we are in a clean state if initialization fails
            shutdown();
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
                log.error("Timeout lors du test Piper");
                return false;
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                log.info("Test Piper réussi avec modèle intégré");
                return true;
            } else {
                log.error("Erreur Piper, code de sortie: {}", exitCode);

                // Read both stdout and stderr for better diagnosis
                try (BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                     BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

                    String line;
                    log.error("--- STDOUT ---");
                    while ((line = stdoutReader.readLine()) != null) {
                        log.error("Piper stdout: {}", line);
                    }

                    log.error("--- STDERR ---");
                    while ((line = stderrReader.readLine()) != null) {
                        log.error("Piper stderr: {}", line);
                    }
                }
                return false;
            }

        } catch (Exception e) {
            log.error("Erreur lors du test Piper", e);
            return false;
        }
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
        log.info("Configuration audio simplifiée - utilisation du mixer par défaut");

        // Test rapide pour vérifier que l'audio fonctionne
        AudioFormat testFormat = new AudioFormat(44100, 16, 2, true, false);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, testFormat);

        if (AudioSystem.isLineSupported(info)) {
            log.info("✓ Support audio confirmé: {}", testFormat);
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
                log.info("TTS traité en {}ms", (endTime - startTime));

                request.markCompleted();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Erreur lors du traitement", e);
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
            command.add("--speaker");
            command.add("1");

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
                log.debug("Fichier audio généré: {} bytes", outputFile.length());
            } else {
                log.error("Aucun fichier audio généré");
            }

            return process.exitValue() == 0 && outputFile.exists() ? outputFile : null;

        } catch (Exception e) {
            log.error("Erreur lors de la synthèse", e);
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
        log.debug("=== LECTURE AUDIO SIMPLIFIÉE ===");
        log.debug("Fichier: {}", audioFile.getAbsolutePath());
        log.debug("Taille: {} bytes", audioFile.length());

        try (AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile)) {

            AudioFormat audioFormat = audioStream.getFormat();
            log.debug("Format du fichier: {}", audioFormat);

            // Créer DataLine.Info pour SourceDataLine (pas Clip)
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);

            // Obtenir et configurer la ligne audio
            SourceDataLine sourceDataLine = (SourceDataLine) AudioSystem.getLine(info);
            sourceDataLine.open(audioFormat);
            sourceDataLine.start();

            log.debug("Format de la ligne: {}", sourceDataLine.getFormat());
            log.debug("Début de la lecture...");

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
                    log.trace("Lu: {} bytes", totalBytesPlayed);
                }
            }

            // Attendre que tout l'audio soit joué
            sourceDataLine.drain();
            sourceDataLine.close();

            long endTime = System.currentTimeMillis();
            log.debug("Audio terminé. Total: {} bytes en {}ms", totalBytesPlayed, (endTime - startTime));

        } catch (Exception e) {
            log.error("Erreur lors de la lecture audio", e);
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
                log.info("Modèle changé vers: {}", newModel.getDescription());

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
            log.error("Erreur lors du changement de modèle", e);
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
            log.error("Erreur lors de la synthèse", e);
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
        // The audio playback is now handled per-file, so there's no global audio line to stop.
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

        cleanup();
        log.info("Module TTS fermé");
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
        log.info("Modèles intégrés trouvés: {}", available.size());
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
        // Configure basic logging for the main method to see the output
        // In a real Spring application, this would be handled by the logging configuration
        org.slf4j.Logger mainLog = org.slf4j.LoggerFactory.getLogger(PiperEmbeddedTTSModule.class);

        mainLog.info("=== Module TTS Piper avec modèles intégrés ===");

        // Vérification des modèles disponibles
        List<EmbeddedModel> available = getAvailableModels();
        mainLog.info("Modèles intégrés trouvés: {}", available.size());

        if (available.isEmpty()) {
            mainLog.error("Aucun modèle trouvé dans les ressources!");
            mainLog.error("Assurez-vous d'avoir inclus les fichiers .onnx dans src/main/resources/models/");
            return;
        }

        // Utilisation du premier modèle disponible
        EmbeddedModel selectedModel = available.get(0);
        mainLog.info("Utilisation du modèle: {}", selectedModel.getDescription());

        PiperEmbeddedTTSModule tts = new PiperEmbeddedTTSModule(selectedModel);

        if (!tts.initialize()) {
            mainLog.error("Impossible d'initialiser le module TTS");
            return;
        }

        try {
            // Tests
            tts.speak("Bonjour, Système de communication activé");
            // Test de changement de modèle
            if (available.size() > 1) {
                mainLog.info("Test de changement de modèle...");
                if (tts.switchModel(available.get(1))) {
                    tts.speak("Nouveau modèle de voix activé avec succès.");
                }
            }

            // Configuration
            tts.setSpeechRate(1.2f);
            tts.speak("Voici un test avec une vitesse plus rapide.");

            mainLog.info("Tests terminés avec succès!");

        } catch (Exception e) {
            mainLog.error("Erreur dans le main", e);
        } finally {
            tts.shutdown();
        }
    }
}