package IO.OuputHandling;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.nio.file.StandardCopyOption;

public class KokoroEmbeddedTTSModule {
    private static final String KOKORO_SCRIPT = "kokoro_onnx.py";
    private static final String KOKORO_DIR = System.getProperty("user.home") + "/.kokoro-tts";
    private static final String VENV_DIR = KOKORO_DIR + "/venv";
    private static final String MODEL_URL = "https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/main/onnx/model_quantized.onnx";
    private static final String VOICE_URL = "https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/main/voices/ff_siwis.bin";
    private static final String MODEL_FILE = "model_quantized.onnx";
    private static final String VOICE_FILE = "ff_siwis.bin";

    private final ExecutorService executor;
    private File scriptFile;
    private String pythonExecutable;

    public KokoroEmbeddedTTSModule() {
        this.executor = Executors.newSingleThreadExecutor();
        try {
            initialize();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void initialize() throws Exception {
        // Create necessary directories
        new File(KOKORO_DIR).mkdirs();

        // Load script from resources
        scriptFile = new File(KOKORO_DIR, KOKORO_SCRIPT);
        System.out.println("Extracting Kokoro script from resources to " + scriptFile.getAbsolutePath());
        copyResourceToFile(KOKORO_SCRIPT, scriptFile);

        // Download model and voices if missing
        downloadResources();

        // Verify python installation and script execution
        verifyInstallation();
    }

    private void downloadResources() {
        try {
            File modelFile = new File(KOKORO_DIR, MODEL_FILE);
            if (!modelFile.exists()) {
                System.out.println("Downloading Kokoro model...");
                downloadFile(MODEL_URL, modelFile);
            }

            File voiceFile = new File(KOKORO_DIR, VOICE_FILE);
            if (!voiceFile.exists()) {
                System.out.println("Downloading voice file...");
                downloadFile(VOICE_URL, voiceFile);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to download Kokoro resources", e);
        }
    }

    private void downloadFile(String urlStr, File destFile) throws IOException {
        File tempFile = new File(destFile.getAbsolutePath() + ".tmp");
        java.net.URL url = new java.net.URL(urlStr);
        try (java.io.InputStream in = url.openStream()) {
            java.nio.file.Files.copy(in, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            java.nio.file.Files.move(tempFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // Clean up temp file on failure
            if (tempFile.exists()) {
                tempFile.delete();
            }
            throw e;
        }
    }

    private void copyResourceToFile(String resourcePath, File destFile) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                // If not found in classpath (e.g. running from IDE without packaging), try local file
                File localFile = new File("ARCOS/src/main/resources/" + resourcePath);
                if (localFile.exists()) {
                    Files.copy(localFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    return;
                }
                throw new FileNotFoundException("Resource not found: " + resourcePath);
            }
            destFile.getParentFile().mkdirs();
            Files.copy(is, destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void verifyInstallation() {
        try {
            System.out.println("Verifying Kokoro environment...");

            // Determine system python command
            String systemPython = System.getProperty("os.name").toLowerCase().contains("win") ? "python" : "python3";

            // Check if venv exists, if not create it
            File venvDir = new File(VENV_DIR);
            if (!venvDir.exists()) {
                System.out.println("Creating Python virtual environment in " + VENV_DIR);
                ProcessBuilder pb = new ProcessBuilder(systemPython, "-m", "venv", VENV_DIR);
                pb.inheritIO();
                if (pb.start().waitFor() != 0) {
                    throw new RuntimeException("Failed to create virtual environment");
                }
            }

            // Determine venv python executable
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                pythonExecutable = new File(venvDir, "Scripts/python.exe").getAbsolutePath();
            } else {
                pythonExecutable = new File(venvDir, "bin/python").getAbsolutePath();
            }

            // Install requirements
            System.out.println("Installing dependencies (misaki, num2words, phonemizer-fork, espeakng-loader, onnxruntime, soundfile, numpy)...");
            ProcessBuilder pb = new ProcessBuilder(pythonExecutable, "-m", "pip", "install",
                "misaki", "num2words", "phonemizer-fork", "espeakng-loader", "onnxruntime", "soundfile", "numpy");
            pb.inheritIO();
            if (pb.start().waitFor() != 0) {
                 throw new RuntimeException("Failed to install dependencies");
            }

            // Verify script execution
            pb = new ProcessBuilder(pythonExecutable, scriptFile.getAbsolutePath(), "--help");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("Kokoro help: " + line);
                }
            }
            if (process.waitFor() != 0) {
                throw new RuntimeException("Kokoro script verification failed.");
            }

            System.out.println("Kokoro verification successful!");
        } catch (Exception e) {
            throw new RuntimeException("Error verifying Kokoro installation: " + e.getMessage(), e);
        }
    }

    public Future<Void> speakAsync(String text) {
        return executor.submit(() -> {
            speak(text);
            return null;
        });
    }

    public void speak(String text) {
        // Default params for Kokoro (French)
        speak(text, "f", "ff_siwis", 1.0f);
    }

    // Kept to match potential interface calls, though we don't use piper params
    public void speak(String text, float lengthScale, float noiseScale, float noiseW) {
        speak(text);
    }

    public void speak(String text, String lang, String voice, float speed) {
        try {
            // Create temp file for audio output
            File audioFile = File.createTempFile("kokoro_output_", ".wav");
            audioFile.deleteOnExit();

            System.out.println("Output file: " + audioFile.getAbsolutePath());

            // Build command
            ProcessBuilder pb = new ProcessBuilder(
                    pythonExecutable,
                    scriptFile.getAbsolutePath(),
                    "--text", text,
                    "--output_file", audioFile.getAbsolutePath(),
                    "--lang", lang,
                    "--voice_path", new File(KOKORO_DIR, VOICE_FILE).getAbsolutePath(),
                    "--speed", String.valueOf(speed),
                    "--model_path", new File(KOKORO_DIR, MODEL_FILE).getAbsolutePath()
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read output/error streams to prevent blocking
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("Kokoro: " + line);
                }
            }

            // Wait for completion
            int exitCode = process.waitFor();
            System.out.println("Kokoro exit code: " + exitCode);

            if (exitCode != 0) {
                throw new RuntimeException("Kokoro process failed with exit code: " + exitCode);
            }

            // Verify the audio file was created and has content
            if (!audioFile.exists()) {
                throw new RuntimeException("Audio file was not created: " + audioFile.getAbsolutePath());
            }

            long fileSize = audioFile.length();
            System.out.println("Audio file size: " + fileSize + " bytes");

            if (fileSize == 0) {
                throw new RuntimeException("Audio file is empty");
            }

            // Play audio
            playAudio(audioFile);

        } catch (Exception e) {
            throw new RuntimeException("Failed to synthesize speech", e);
        }
    }

    private void playAudio(File audioFile) throws Exception {
        String os = getOperatingSystem();
        ProcessBuilder pb;

        System.out.println("Playing audio on " + os + ": " + audioFile.getAbsolutePath());

        switch (os) {
            case "linux":
                // Try paplay first (PulseAudio), fallback to aplay
                if (isCommandAvailable("paplay")) {
                    pb = new ProcessBuilder("paplay", audioFile.getAbsolutePath());
                }
                else if (isCommandAvailable("aplay")) {
                    pb = new ProcessBuilder("aplay", audioFile.getAbsolutePath());
                }
                else {
                    // Try ffplay or generic
                    System.err.println("No standard audio player found (paplay, aplay). Trying to continue without playing.");
                    return;
                }
                break;
            case "macos":
                pb = new ProcessBuilder("afplay", audioFile.getAbsolutePath());
                break;
            case "windows":
                pb = new ProcessBuilder("powershell", "-c",
                        "(New-Object Media.SoundPlayer '" + audioFile.getAbsolutePath() + "').PlaySync()");
                break;
            default:
                throw new UnsupportedOperationException("Audio playback not supported on: " + os);
        }

        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Read output/error for debugging
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("Audio player: " + line);
            }
        }

        int exitCode = process.waitFor();
        System.out.println("Audio player exit code: " + exitCode);

        if (exitCode != 0) {
            throw new RuntimeException("Audio playback failed with exit code: " + exitCode);
        }
    }

    private boolean isCommandAvailable(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder("which", command);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String getOperatingSystem() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "macos";
        if (os.contains("nux")) return "linux";
        throw new UnsupportedOperationException("Unsupported OS: " + os);
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}
