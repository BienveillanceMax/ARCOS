package IO.OuputHandling;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;

public class KokoroEmbeddedTTSModule {
    private static final String KOKORO_SCRIPT = "kokoro_tts.py";
    private static final String KOKORO_DIR = System.getProperty("user.home") + "/.kokoro-tts";

    private final ExecutorService executor;
    private File scriptFile;

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

        // Verify python installation and script execution
        verifyInstallation();
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
            System.out.println("Verifying Kokoro script at: " + scriptFile.getAbsolutePath());

            // Check if python3 is available
            ProcessBuilder pb = new ProcessBuilder("python3", "--version");
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                pb = new ProcessBuilder("python", "--version");
            }

            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new RuntimeException("Python 3 not found. Please install Python 3.");
            }

            // Check if kokoro is installed (optional, maybe just check if we can run help on script)
            pb = new ProcessBuilder(
                    System.getProperty("os.name").toLowerCase().contains("win") ? "python" : "python3",
                    scriptFile.getAbsolutePath(),
                    "--help"
            );
            pb.redirectErrorStream(true);
            process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // System.out.println("Kokoro help: " + line);
                }
            }
            exitCode = process.waitFor();
            if (exitCode != 0) {
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
                    System.getProperty("os.name").toLowerCase().contains("win") ? "python" : "python3",
                    scriptFile.getAbsolutePath(),
                    "--text", text,
                    "--output_file", audioFile.getAbsolutePath(),
                    "--lang", lang,
                    "--voice", voice,
                    "--speed", String.valueOf(speed)
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
