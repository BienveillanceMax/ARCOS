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
        // Embed the script content directly to avoid resource loading issues
        String scriptContent = "import argparse\n" +
"import os\n" +
"import json\n" +
"import numpy as np\n" +
"import soundfile as sf\n" +
"import onnxruntime as ort\n" +
"from misaki import en, espeak\n" +
"\n" +
"def main():\n" +
"    parser = argparse.ArgumentParser(description='Kokoro TTS Inference')\n" +
"    parser.add_argument('--text', type=str, required=True, help='Text to speak')\n" +
"    parser.add_argument('--output_file', type=str, required=True, help='Output WAV file path')\n" +
"    parser.add_argument('--lang', type=str, default='f', help='Language code (default: f)')\n" +
"    parser.add_argument('--voice_path', type=str, required=True, help='Path to voice .bin file')\n" +
"    parser.add_argument('--speed', type=float, default=1.0, help='Speech speed')\n" +
"    parser.add_argument('--model_path', type=str, default='model_quantized.onnx', help='Path to ONNX model')\n" +
"    \n" +
"    args = parser.parse_args()\n" +
"\n" +
"    # Load voice\n" +
"    # Voice is stored as a raw numpy array, typically (256,) or (1, 256)\n" +
"    try:\n" +
"        voice_style = np.fromfile(args.voice_path, dtype=np.float32)\n" +
"    except Exception as e:\n" +
"        print(f\"Error loading voice file: {e}\")\n" +
"        return\n" +
"\n" +
"    # Check shape and reshape if necessary\n" +
"    # Model expects (1, 256) usually\n" +
"    if voice_style.ndim == 1:\n" +
"        voice_style = voice_style.reshape(1, -1) # (1, 256)\n" +
"    \n" +
"    # Tokenize\n" +
"    # Kokoro v0.19 uses misaki for G2P.\n" +
"    # Lang 'f' for French.\n" +
"    \n" +
"    phonemes = \"\"\n" +
"    if args.lang == 'f':\n" +
"        try:\n" +
"            # Try to use misaki's french support via espeak-ng if available\n" +
"            # Note: misaki might not have direct 'fr' function like `en`\n" +
"            # We use espeak via misaki or directly\n" +
"            phonemes = espeak.phonemize(args.text, args.lang)\n" +
"        except Exception as e:\n" +
"            print(f\"Error phonemizing: {e}\")\n" +
"            return\n" +
"    else:\n" +
"         # Fallback or other languages\n" +
"         phonemes = espeak.phonemize(args.text, args.lang)\n" +
"    \n" +
"    if not phonemes:\n" +
"        print(\"Empty phonemes result\")\n" +
"        return\n" +
"\n" +
"    # Tokenize phonemes\n" +
"    # Kokoro has a specific vocabulary.\n" +
"    # v0.19 vocab is standard.\n" +
"    \n" +
"    vocab = \"pad_ $;,.?!\\u00a1\\u00bf\\u2014\\u2026\\u2191\\u2193\\u201c\\u201d\\u00e0\\u00e1\\u00e2\\u00e3\\u00e4\\u00e5\\u00e6\\u00e7\\u00e8\\u00e9\\u00ea\\u00eb\\u00ec\\u00ed\\u00ee\\u00ef\\u00f1\\u00f2\\u00f3\\u00f4\\u00f5\\u00f6\\u00f9\\u00fa\\u00fb\\u00fc\\u00ff\\u0101\\u0105\\u0107\\u010d\\u0113\\u0119\\u011b\\u012b\\u0131\\u0142\\u0144\\u014d\\u0151\\u0153\\u015b\\u0161\\u016b\\u017a\\u017c\\u017e\\u017f\\u0250\\u0251\\u0252\\u0253\\u0254\\u0255\\u0256\\u0257\\u0259\\u025a\\u025b\\u025c\\u025d\\u025e\\u025f\\u0260\\u0261\\u0262\\u0263\\u0264\\u0265\\u0266\\u0267\\u0268\\u0269\\u026a\\u026b\\u026c\\u026d\\u026e\\u026f\\u0270\\u0271\\u0272\\u0273\\u0274\\u0275\\u0276\\u0277\\u0278\\u0279\\u027a\\u027b\\u027c\\u027d\\u027e\\u027f\\u0280\\u0281\\u0282\\u0283\\u0284\\u0285\\u0286\\u0287\\u0288\\u0289\\u028a\\u028b\\u028c\\u028d\\u028e\\u028f\\u0290\\u0291\\u0292\\u0293\\u0294\\u0295\\u0296\\u0297\\u0298\\u0299\\u029a\\u029b\\u029c\\u029d\\u029e\\u029f\\u02a0\\u02a1\\u02a2\\u02a3\\u02a4\\u02a5\\u02a6\\u02a7\\u02a8\\u02ac\\u02b0\\u02b1\\u02b2\\u02b4\\u02b7\\u02b9\\u02bb\\u02bc\\u02bd\\u02be\\u02bf\\u02c0\\u02c1\\u02c8\\u02cc\\u02d0\\u02d1\\u0300\\u0301\\u0302\\u0303\\u0304\\u0306\\u0308\\u030a\\u030b\\u030c\\u030f\\u0311\\u031a\\u031c\\u0323\\u0324\\u0325\\u0329\\u032f\\u0330\\u0339\\u033d\\u1d00\\u1d07\\u1d0a\\u1d0d\\u1d1c\\u1d20\\u1d21\\u1d25\\u1d6a\\u1d77\\u1d79\\u1d96\\u207f\"\n" +
"    token_map = {c: i for i, c in enumerate(vocab)}\n" +
"    \n" +
"    tokens = [token_map.get(c, 0) for c in phonemes]\n" +
"    \n" +
"    # Truncate if too long (510 max usually for BERT-like, but Kokoro context is 512)\n" +
"    if len(tokens) > 510:\n" +
"        tokens = tokens[:510]\n" +
"        \n" +
"    input_ids = [0] + tokens + [0]\n" +
"    \n" +
"    # Inference\n" +
"    sess = ort.InferenceSession(args.model_path)\n" +
"    \n" +
"    # Check model inputs\n" +
"    # inputs: input_ids, style, speed\n" +
"    \n" +
"    inputs = {\n" +
"        'input_ids': np.array([input_ids], dtype=np.int64),\n" +
"        'style': voice_style,\n" +
"        'speed': np.array([args.speed], dtype=np.float32)\n" +
"    }\n" +
"    \n" +
"    audio = sess.run(None, inputs)[0]\n" +
"    \n" +
"    # Save\n" +
"    sf.write(args.output_file, audio[0], 24000)\n" +
"\n" +
"if __name__ == \"__main__\":\n" +
"    main()\n";

        destFile.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(destFile)) {
            writer.write(scriptContent);
        }
        System.out.println("Written embedded Kokoro script to " + destFile.getAbsolutePath());
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
