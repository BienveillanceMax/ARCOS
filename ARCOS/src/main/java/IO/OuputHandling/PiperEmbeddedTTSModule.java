package IO.OuputHandling;
import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class PiperEmbeddedTTSModule {
    private static final String PIPER_VERSION = "2023.11.14-2";
    private static final String PIPER_DIR = System.getProperty("user.home") + "/.piper-tts";
    private static final String MODEL_DIR = PIPER_DIR + "/models";
    private static final String UPMC_MODEL_PATH = "upmc-model/fr_FR-upmc-medium.onnx";
    private static final String UPMC_CONFIG_PATH = "upmc-model/fr_FR-upmc-medium.onnx.json";

    private final ExecutorService executor;
    private String piperExecutable;
    private File modelFile;
    private File configFile;

    public PiperEmbeddedTTSModule() {
        this.executor = Executors.newSingleThreadExecutor();
        try {
            initialize();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void initialize() throws Exception {
        // Create necessary directories
        new File(PIPER_DIR).mkdirs();
        new File(MODEL_DIR).mkdirs();

        // Check for Piper installation
        if (!isPiperInstalled()) {
            System.out.println("Piper TTS not found. Downloading and installing...");
            downloadAndInstallPiper();
        } else {
            System.out.println("Piper TTS found at: " + piperExecutable);
        }

        // Load model from resources
        loadModelFromResources();

        // Verify installation
        verifyInstallation();
    }

    private boolean isPiperInstalled() {
        String os = getOperatingSystem();
        String executable = PIPER_DIR + "/piper/piper" + (os.equals("windows") ? ".exe" : "");
        File piperFile = new File(executable);

        if (piperFile.exists() && piperFile.canExecute()) {
            piperExecutable = executable;
            return true;
        }
        return false;
    }

    private void downloadAndInstallPiper() throws Exception {
        String os = getOperatingSystem();
        String arch = getArchitecture();
        String downloadUrl = getPiperDownloadUrl(os, arch);

        System.out.println("Downloading from: " + downloadUrl);

        // Determine file extension based on URL
        String fileName = downloadUrl.endsWith(".zip") ? "piper.zip" : "piper.tar.gz";
        File archiveFile = new File(PIPER_DIR, fileName);

        try (InputStream in = new URL(downloadUrl).openStream()) {
            Files.copy(in, archiveFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        System.out.println("Downloaded to: " + archiveFile.getAbsolutePath());

        // Extract archive
        System.out.println("Extracting archive...");
        extractArchive(archiveFile, new File(PIPER_DIR));

        System.out.println("Extraction complete, deleting archive...");
        archiveFile.delete();

        // List files after extraction for debugging
        System.out.println("Files after extraction:");
        listFiles(new File(PIPER_DIR), "  ");

        // Find and set executable
        findPiperExecutable();

        if (piperExecutable == null) {
            throw new RuntimeException("Could not locate piper executable after extraction");
        }

        System.out.println("Found Piper executable at: " + piperExecutable);

        // Make executable on Unix systems
        if (!os.equals("windows")) {
            File exeFile = new File(piperExecutable);
            exeFile.setExecutable(true);
            System.out.println("Set executable permissions");
        }
    }

    private void listFiles(File dir, String indent) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                System.out.println(indent + file.getName() + (file.isDirectory() ? "/" : ""));
                if (file.isDirectory() && !file.getName().equals("models")) {
                    listFiles(file, indent + "  ");
                }
            }
        }
    }

    private String getPiperDownloadUrl(String os, String arch) {
        String platform;

        switch (os) {
            case "linux":
                platform = arch.equals("aarch64") ? "aarch64" : "x86_64";
                return String.format("https://github.com/rhasspy/piper/releases/download/%s/piper_linux_%s.tar.gz",
                        PIPER_VERSION, platform);
            case "macos":
                platform = arch.equals("aarch64") ? "arm64" : "x64";
                return String.format("https://github.com/rhasspy/piper/releases/download/%s/piper_macos_%s.tar.gz",
                        PIPER_VERSION, platform);
            case "windows":
                return String.format("https://github.com/rhasspy/piper/releases/download/%s/piper_windows_amd64.zip",
                        PIPER_VERSION);
            default:
                throw new UnsupportedOperationException("Unsupported OS: " + os);
        }
    }

    private void extractArchive(File archive, File destDir) throws Exception {
        System.out.println("Extracting " + archive.getName() + " to " + destDir.getAbsolutePath());
        if (archive.getName().endsWith(".zip")) {
            extractZip(archive, destDir);
        } else if (archive.getName().endsWith(".tar.gz") || archive.getName().endsWith(".tgz")) {
            extractTarGz(archive, destDir);
        } else {
            throw new IllegalArgumentException("Unsupported archive format: " + archive.getName());
        }
    }

    private void extractZip(File zipFile, File destDir) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File file = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    file.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private void extractTarGz(File tarGzFile, File destDir) throws Exception {
        // Check if tar command is available
        try {
            ProcessBuilder pb = new ProcessBuilder("tar", "-xzf", tarGzFile.getAbsolutePath(), "-C", destDir.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read output for debugging
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("tar: " + line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Failed to extract tar.gz file, exit code: " + exitCode);
            }
        } catch (IOException e) {
            System.err.println("tar command not available, trying alternative extraction...");
            // Fallback: manual extraction using Java (requires additional libraries)
            throw new RuntimeException("tar command not available. Please install tar or manually extract Piper.", e);
        }
    }

    private void findPiperExecutable() {
        String os = getOperatingSystem();
        String exeName = os.equals("windows") ? "piper.exe" : "piper";

        System.out.println("Searching for " + exeName + " in " + PIPER_DIR);

        // Search in PIPER_DIR and subdirectories
        File[] files = new File(PIPER_DIR).listFiles();
        if (files != null) {
            System.out.println("Found " + files.length + " files/directories in PIPER_DIR");
            for (File file : files) {
                System.out.println("  Checking: " + file.getName());
                File exe = findFileRecursive(file, exeName);
                if (exe != null) {
                    piperExecutable = exe.getAbsolutePath();
                    System.out.println("Found executable at: " + piperExecutable);

                    // Set executable permissions immediately on Unix systems
                    if (!os.equals("windows")) {
                        try {
                            exe.setExecutable(true, false);
                            // Also try using chmod for better reliability
                            try {
                                ProcessBuilder pb = new ProcessBuilder("chmod", "+x", exe.getAbsolutePath());
                                Process p = pb.start();
                                p.waitFor();
                                System.out.println("Set executable permissions using chmod");
                            } catch (Exception e) {
                                System.out.println("chmod failed, using Java setExecutable: " + e.getMessage());
                            }
                        } catch (Exception e) {
                            System.err.println("Failed to set executable permissions: " + e.getMessage());
                        }
                    }
                    return;
                }
            }
        }

        // Fallback to expected location
        piperExecutable = PIPER_DIR + "/" + exeName;
        System.out.println("Using fallback path: " + piperExecutable);
    }

    private File findFileRecursive(File dir, String filename) {
        if (dir.isFile() && dir.getName().equals(filename)) {
            System.out.println("    Found file: " + dir.getAbsolutePath());
            return dir;
        }
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    System.out.println("    Scanning: " + file.getAbsolutePath() + (file.isDirectory() ? " (dir)" : " (file)"));
                    File result = findFileRecursive(file, filename);
                    if (result != null) return result;
                }
            }
        }
        return null;
    }

    private void loadModelFromResources() throws Exception {
        modelFile = new File(MODEL_DIR, "fr_FR-upmc-medium.onnx");
        configFile = new File(MODEL_DIR, "fr_FR-upmc-medium.onnx.json");

        // Try to load from resources
        if (!modelFile.exists()) {
            System.out.println("Extracting model from resources...");
            copyResourceToFile(UPMC_MODEL_PATH, modelFile);
        }

        if (!configFile.exists()) {
            System.out.println("Extracting config from resources...");
            copyResourceToFile(UPMC_CONFIG_PATH, configFile);
        }

        if (!modelFile.exists() || !configFile.exists()) {
            throw new FileNotFoundException("Model files not found in resources or could not be extracted");
        }
    }

    private void copyResourceToFile(String resourcePath, File destFile) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new FileNotFoundException("Resource not found: " + resourcePath);
            }
            destFile.getParentFile().mkdirs();
            Files.copy(is, destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void verifyInstallation() {
        try {
            System.out.println("Verifying Piper installation at: " + piperExecutable);

            File exeFile = new File(piperExecutable);
            if (!exeFile.exists()) {
                throw new RuntimeException("Piper executable not found at: " + piperExecutable);
            }

            if (exeFile.isDirectory()) {
                throw new RuntimeException("Piper path is a directory, not a file: " + piperExecutable);
            }

            // Double-check and set executable permissions before running
            String os = getOperatingSystem();
            if (!os.equals("windows") && !exeFile.canExecute()) {
                System.err.println("Piper executable is not executable, attempting to fix...");
                exeFile.setExecutable(true, false);

                // Try chmod as backup
                try {
                    ProcessBuilder chmodPb = new ProcessBuilder("chmod", "+x", piperExecutable);
                    Process chmodProcess = chmodPb.start();
                    chmodProcess.waitFor();
                    System.out.println("Applied chmod +x to " + piperExecutable);
                } catch (Exception e) {
                    System.err.println("chmod failed: " + e.getMessage());
                }

                // Verify it's now executable
                if (!exeFile.canExecute()) {
                    throw new RuntimeException("Failed to make file executable: " + piperExecutable);
                }
            }

            ProcessBuilder pb = new ProcessBuilder(piperExecutable, "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read output for debugging
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                System.out.println("Piper output:");
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }

            int exitCode = process.waitFor();
            System.out.println("Piper verification exit code: " + exitCode);

            if (exitCode != 0) {
                throw new RuntimeException("Piper verification failed with exit code: " + exitCode);
            }

            System.out.println("Piper verification successful!");
        } catch (Exception e) {
            throw new RuntimeException("Error verifying Piper installation: " + e.getMessage(), e);
        }
    }

    public Future<Void> speakAsync(String text) {
        return executor.submit(() -> {
            speak(text);
            return null;
        });
    }

    public void speak(String text) {
        speak(text, 1.0f, 0.667f, 0.8f);
    }

    public void speak(String text, float lengthScale, float noiseScale, float noiseW) {
        try {
            // Create temp file for audio output
            File audioFile = File.createTempFile("piper_output_", ".wav");
            audioFile.deleteOnExit();

            System.out.println("Output file: " + audioFile.getAbsolutePath());

            // Build command
            ProcessBuilder pb = new ProcessBuilder(
                    piperExecutable,
                    "--speaker","1",
                    "--model", modelFile.getAbsolutePath(),
                    "--config", configFile.getAbsolutePath(),
                    "--output_file", audioFile.getAbsolutePath(),
                    "--length_scale", String.valueOf(lengthScale),
                    "--noise_scale", String.valueOf(noiseScale),
                    "--noise_w", String.valueOf(noiseW)
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Write text to stdin
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
                writer.write(text);
                writer.flush();
            }

            // Read output/error streams to prevent blocking
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("Piper: " + line);
                }
            }

            // Wait for completion
            int exitCode = process.waitFor();
            System.out.println("Piper exit code: " + exitCode);

            if (exitCode != 0) {
                throw new RuntimeException("Piper process failed with exit code: " + exitCode);
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
                else {
                    throw new RuntimeException("No audio player found. Install pulseaudio-utils or alsa-utils");
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

    private String getArchitecture() {
        String arch = System.getProperty("os.arch").toLowerCase();
        if (arch.contains("aarch64") || arch.contains("arm64")) return "aarch64";
        return "x86_64";
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