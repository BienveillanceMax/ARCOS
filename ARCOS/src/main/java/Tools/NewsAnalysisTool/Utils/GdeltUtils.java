package Tools.NewsAnalysisTool.Utils;

import Tools.NewsAnalysisTool.models.GdeltEvent;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Utilitaires pour le service GDELT
 */
public class GdeltUtils
{

    private static final DateTimeFormatter GDELT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://.*");

    /**
     * Formate une date pour les requêtes GDELT
     */
    public static String formatDateForGdelt(LocalDateTime date) {
        return date.format(GDELT_DATE_FORMAT);
    }

    /**
     * Valide si une URL est correctement formatée
     */
    public static boolean isValidUrl(String url) {
        return url != null && URL_PATTERN.matcher(url).matches();
    }

    /**
     * Calcule un score de pertinence pour un événement
     */
    public static double calculateRelevanceScore(GdeltEvent event, String searchTopic) {
        double score = 0.0;

        // Score basé sur le nombre de mentions
        if (event.getNumMentions() != null) {
            score += Math.log(event.getNumMentions() + 1) * 0.3;
        }

        // Score basé sur l'échelle de Goldstein (impact)
        if (event.getGoldsteinScale() != null) {
            score += Math.abs(event.getGoldsteinScale()) * 0.2;
        }

        // Score basé sur le nombre de sources
        if (event.getNumSources() != null) {
            score += Math.log(event.getNumSources() + 1) * 0.2;
        }

        // Bonus pour correspondance avec le nom d'acteur
        if (searchTopic != null && event.getActor1Name() != null &&
                event.getActor1Name().toLowerCase().contains(searchTopic.toLowerCase())) {
            score += 1.0;
        }

        return score;
    }

    /**
     * Normalise un nom de pays pour les requêtes GDELT
     */
    public static String normalizeCountryName(String countryName) {
        if (countryName == null) return null;

        // Mapping des noms de pays vers codes ISO
        return switch (countryName.toLowerCase()) {
            case "france", "french" -> "FR";
            case "germany", "german" -> "DE";
            case "united states", "usa", "america", "american" -> "US";
            case "united kingdom", "uk", "britain", "british" -> "GB";
            case "china", "chinese" -> "CN";
            case "russia", "russian" -> "RU";
            case "japan", "japanese" -> "JP";
            case "india", "indian" -> "IN";
            case "brazil", "brazilian" -> "BR";
            default -> countryName.toUpperCase();
        };
    }

    /**
     * Extrait les mots-clés d'une chaîne de recherche
     */
    public static List<String> extractKeywords(String searchQuery) {
        if (searchQuery == null) return List.of();

        return List.of(searchQuery.toLowerCase()
                .replaceAll("[^a-zA-Z0-9\\s]", "")
                .split("\\s+"));
    }

    /**
     * Génère une signature unique pour un événement
     */
    public static String generateEventSignature(GdeltEvent event) {
        StringBuilder signature = new StringBuilder();

        if (event.getActor1Name() != null) {
            signature.append(event.getActor1Name().hashCode());
        }

        if (event.getActor2Name() != null) {
            signature.append("-").append(event.getActor2Name().hashCode());
        }

        if (event.getEventCode() != null) {
            signature.append("-").append(event.getEventCode());
        }

        if (event.getEventDate() != null) {
            signature.append("-").append(event.getEventDate().toLocalDate());
        }

        return signature.toString();
    }

    /**
     * Vérifie si un événement est considéré comme récent
     */
    public static boolean isRecentEvent(GdeltEvent event, int daysThreshold) {
        if (event.getEventDate() == null) return false;

        LocalDateTime threshold = LocalDateTime.now().minusDays(daysThreshold);
        return event.getEventDate().isAfter(threshold);
    }

    /**
     * Calcule la distance temporelle entre deux événements en heures
     */
    public static long calculateTemporalDistance(GdeltEvent event1, GdeltEvent event2) {
        if (event1.getEventDate() == null || event2.getEventDate() == null) {
            return Long.MAX_VALUE;
        }

        return Math.abs(java.time.Duration.between(event1.getEventDate(), event2.getEventDate()).toHours());
    }

    /**
     * Détermine la criticité d'un événement basée sur l'échelle de Goldstein
     */
    public static String determineCriticality(GdeltEvent event) {
        if (event.getGoldsteinScale() == null) return "INDETERMINEE";

        double goldstein = Math.abs(event.getGoldsteinScale());

        if (goldstein >= 8.0) return "CRITIQUE";
        if (goldstein >= 5.0) return "ELEVEE";
        if (goldstein >= 2.0) return "MODEREE";
        return "FAIBLE";
    }
}
