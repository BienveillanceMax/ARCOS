package org.arcos.Setup.Health;

import java.io.File;

/**
 * Vérifie la disponibilité de Piper TTS en cherchant l'exécutable et le modèle.
 */
public class PiperHealthChecker implements ServiceHealthCheck {

    private static final String PIPER_DIR = System.getProperty("user.home") + "/.piper-tts";
    private static final String MODEL_NAME = "fr_FR-glados-medium.onnx";

    @Override
    public String serviceName() {
        return "Piper TTS";
    }

    @Override
    public HealthResult check(ServiceConfig config) {
        // Cherche l'exécutable piper
        File piperDir = new File(PIPER_DIR);
        File piperExe = findPiperExecutable(piperDir);

        if (piperExe == null || !piperExe.canExecute()) {
            return HealthResult.offline("Exécutable piper introuvable dans " + PIPER_DIR);
        }

        // Vérifie le modèle
        File modelDir = new File(PIPER_DIR + "/models");
        File modelFile = new File(modelDir, MODEL_NAME);

        if (!modelFile.exists()) {
            return HealthResult.offline("Modèle " + MODEL_NAME + " introuvable — premier lancement téléchargera le modèle");
        }

        return HealthResult.online("Piper + modèle " + MODEL_NAME, 0);
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
}
