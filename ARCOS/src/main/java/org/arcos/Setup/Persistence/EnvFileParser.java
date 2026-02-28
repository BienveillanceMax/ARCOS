package org.arcos.Setup.Persistence;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lit et parse un fichier .env au format KEY=VALUE.
 * Supporte les commentaires (#), les lignes vides, et les valeurs entre guillemets.
 * Aucune dépendance Spring.
 */
public class EnvFileParser {

    private EnvFileParser() {}

    /**
     * Parse un fichier .env et retourne une Map KEY → VALUE.
     * Les lignes commençant par # sont ignorées.
     * Les guillemets simples/doubles autour des valeurs sont supprimés.
     *
     * @param envFile fichier .env à lire
     * @return map des variables d'environnement (ordre d'insertion conservé)
     * @throws IOException si le fichier ne peut pas être lu
     */
    public static Map<String, String> parse(File envFile) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        if (!envFile.exists()) return result;

        try (BufferedReader reader = new BufferedReader(new FileReader(envFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                int equalsIdx = line.indexOf('=');
                if (equalsIdx <= 0) continue;

                String key = line.substring(0, equalsIdx).trim();
                String value = line.substring(equalsIdx + 1).trim();
                value = stripQuotes(value);
                result.put(key, value);
            }
        }
        return result;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    /**
     * Charge un fichier .env comme ConfigurationModel pré-rempli.
     *
     * @param envFile fichier .env
     * @return ConfigurationModel avec les valeurs existantes chargées
     */
    public static org.arcos.Setup.ConfigurationModel loadIntoModel(File envFile) throws IOException {
        Map<String, String> vars = parse(envFile);
        org.arcos.Setup.ConfigurationModel model = new org.arcos.Setup.ConfigurationModel();

        if (vars.containsKey("MISTRALAI_API_KEY")) {
            model.setMistralApiKey(vars.get("MISTRALAI_API_KEY"));
        }
        if (vars.containsKey("BRAVE_SEARCH_API_KEY")) {
            model.setBraveSearchApiKey(vars.get("BRAVE_SEARCH_API_KEY"));
        }
        if (vars.containsKey("PORCUPINE_ACCESS_KEY")) {
            model.setPorcupineAccessKey(vars.get("PORCUPINE_ACCESS_KEY"));
        }
        model.setLoadedFromExistingEnv(true);
        return model;
    }
}
