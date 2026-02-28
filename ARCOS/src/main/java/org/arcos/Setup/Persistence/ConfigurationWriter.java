package org.arcos.Setup.Persistence;

import org.arcos.Setup.ConfigurationModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

/**
 * Écrit la configuration collectée par le wizard dans :
 * - .env        (clés API, permissions 600)
 * - application-local.yaml  (profil audio / personnalité, pour Spring Boot)
 *
 * Toutes les écritures sont atomiques : fichier temporaire → rename.
 * Aucune dépendance Spring.
 */
public class ConfigurationWriter {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationWriter.class);

    private final File envFile;
    private final File localYamlFile;

    public ConfigurationWriter() {
        this(new File(".env"), new File("application-local.yaml"));
    }

    public ConfigurationWriter(File envFile, File localYamlFile) {
        this.envFile = envFile;
        this.localYamlFile = localYamlFile;
    }

    /**
     * Sauvegarde toute la configuration de façon atomique.
     *
     * @param model modèle de configuration collecté par le wizard
     * @throws IOException en cas d'erreur d'écriture
     */
    public void save(ConfigurationModel model) throws IOException {
        writeEnvFile(model);
        writeLocalYaml(model);
        log.info("Configuration sauvegardée avec succès.");
    }

    /**
     * Écrit le fichier .env avec les clés API.
     * Les clés vides/nulles sont écrites comme des placeholders commentés.
     */
    private void writeEnvFile(ConfigurationModel model) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# Configuration ARCOS — généré par le wizard de configuration\n");
        sb.append("# NE PAS committer ce fichier — contient des clés API sensibles\n\n");

        sb.append("# Clé API Mistral AI (OBLIGATOIRE)\n");
        appendKeyLine(sb, "MISTRALAI_API_KEY", model.getMistralApiKey());

        sb.append("\n# Clé API Brave Search (optionnel — recherche web)\n");
        appendKeyLine(sb, "BRAVE_SEARCH_API_KEY", model.getBraveSearchApiKey());

        sb.append("\n# Clé Porcupine (optionnel — détection du mot de réveil)\n");
        appendKeyLine(sb, "PORCUPINE_ACCESS_KEY", model.getPorcupineAccessKey());

        writeAtomically(envFile.toPath(), sb.toString());
        applySecurePermissions(envFile.toPath());
        log.info("Fichier .env sauvegardé : {}", envFile.getAbsolutePath());
    }

    private void appendKeyLine(StringBuilder sb, String key, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(key).append("=").append(value).append("\n");
        } else {
            sb.append("# ").append(key).append("=your_key_here\n");
        }
    }

    /**
     * Écrit application-local.yaml avec les propriétés Spring (audio, personnalité).
     */
    private void writeLocalYaml(ConfigurationModel model) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# Configuration locale ARCOS — généré par le wizard de configuration\n");
        sb.append("# Ce fichier est chargé par spring.config.import=optional:file:./application-local.yaml\n\n");

        sb.append("arcos:\n");

        // Audio
        if (model.getAudioDeviceIndex() >= 0) {
            sb.append("  audio:\n");
            sb.append("    input-device-index: ").append(model.getAudioDeviceIndex()).append("\n");
        }

        // Personnalité
        if (model.getPersonalityProfile() != null && !model.getPersonalityProfile().isBlank()) {
            sb.append("  personality:\n");
            sb.append("    profile: ").append(model.getPersonalityProfile()).append("\n");
        }

        writeAtomically(localYamlFile.toPath(), sb.toString());
        log.info("Fichier application-local.yaml sauvegardé : {}", localYamlFile.getAbsolutePath());
    }

    /**
     * Écrit le contenu dans un fichier de façon atomique (temp → rename).
     */
    private void writeAtomically(Path targetPath, String content) throws IOException {
        Path tempPath = Path.of(targetPath + ".tmp");
        Files.writeString(tempPath, content);
        Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * Applique les permissions 600 (rw-------) sur le fichier .env.
     * Ne fait rien sur Windows (pas de permissions POSIX).
     */
    private void applySecurePermissions(Path path) {
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            );
            Files.setPosixFilePermissions(path, perms);
            log.debug("Permissions 600 appliquées sur {}", path);
        } catch (UnsupportedOperationException e) {
            log.debug("Permissions POSIX non supportées (Windows) — ignoré.");
        } catch (IOException e) {
            log.warn("Impossible d'appliquer les permissions sur {} : {}", path, e.getMessage());
        }
    }

    public File getEnvFile() {
        return envFile;
    }

    public File getLocalYamlFile() {
        return localYamlFile;
    }
}
