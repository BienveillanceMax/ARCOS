package org.arcos.Setup.Detection;

import org.arcos.Setup.ConfigurationModel;
import org.arcos.Setup.Persistence.EnvFileParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Détecte si la configuration ARCOS est complète avant le démarrage Spring.
 * Vérifie la présence de MISTRALAI_API_KEY dans :
 * 1. Les variables d'environnement courantes (déjà exportées dans le shell)
 * 2. Le fichier .env dans le répertoire de travail
 *
 * Aucune dépendance Spring.
 */
public class ConfigurationDetector {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationDetector.class);

    private final File envFile;

    public ConfigurationDetector() {
        this(new File(".env"));
    }

    public ConfigurationDetector(File envFile) {
        this.envFile = envFile;
    }

    /**
     * @return true si la configuration minimale obligatoire est présente
     *         (MISTRALAI_API_KEY non vide dans l'environnement ou dans le .env)
     */
    public boolean isConfigurationComplete() {
        // 1. Vérifier les variables d'environnement déjà chargées
        String mistralKey = System.getenv("MISTRALAI_API_KEY");
        if (mistralKey != null && !mistralKey.isBlank()) {
            log.debug("Configuration complète via variables d'environnement.");
            return true;
        }

        // 2. Vérifier le fichier .env
        if (envFile.exists()) {
            try {
                ConfigurationModel model = EnvFileParser.loadIntoModel(envFile);
                if (model.isMinimallyComplete()) {
                    log.debug("Configuration complète via fichier .env : {}", envFile.getAbsolutePath());
                    return true;
                }
            } catch (IOException e) {
                log.warn("Impossible de lire le fichier .env : {}", e.getMessage());
            }
        }

        log.debug("Configuration incomplète — MISTRALAI_API_KEY absent.");
        return false;
    }

    /**
     * Charge les valeurs existantes (depuis .env ou variables d'env) dans un ConfigurationModel.
     * Utilisé pour pré-remplir le wizard en mode --setup/--reconfigure.
     *
     * @return ConfigurationModel avec les valeurs existantes, ou un modèle vide
     */
    public ConfigurationModel loadExistingConfiguration() {
        // 1. Tenter de charger depuis .env
        if (envFile.exists()) {
            try {
                ConfigurationModel model = EnvFileParser.loadIntoModel(envFile);
                log.debug("Configuration existante chargée depuis {}", envFile.getAbsolutePath());
                return model;
            } catch (IOException e) {
                log.warn("Impossible de lire le fichier .env : {}", e.getMessage());
            }
        }

        // 2. Fallback : variables d'environnement
        ConfigurationModel model = new ConfigurationModel();
        String mistralKey = System.getenv("MISTRALAI_API_KEY");
        if (mistralKey != null && !mistralKey.isBlank()) {
            model.setMistralApiKey(mistralKey);
        }
        String braveKey = System.getenv("BRAVE_SEARCH_API_KEY");
        if (braveKey != null && !braveKey.isBlank()) {
            model.setBraveSearchApiKey(braveKey);
        }
        String porcupineKey = System.getenv("PORCUPINE_ACCESS_KEY");
        if (porcupineKey != null && !porcupineKey.isBlank()) {
            model.setPorcupineAccessKey(porcupineKey);
        }
        return model;
    }

    public File getEnvFile() {
        return envFile;
    }
}
