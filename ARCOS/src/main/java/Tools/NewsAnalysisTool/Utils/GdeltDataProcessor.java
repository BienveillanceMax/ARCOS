package Tools.NewsAnalysisTool.Utils;

import Tools.NewsAnalysisTool.models.GdeltEvent;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service de validation et de nettoyage des données GDELT
 */
@Service
public class GdeltDataProcessor
{

    /**
     * Valide et nettoie une liste d'événements GDELT
     */
    public List<GdeltEvent> processEvents(List<GdeltEvent> rawEvents) {
        return rawEvents.stream()
                .filter(this::isValidEvent)
                .map(this::cleanEvent)
                .collect(Collectors.toList());
    }

    /**
     * Déduplique les événements basés sur leur signature
     */
    public List<GdeltEvent> deduplicateEvents(List<GdeltEvent> events) {
        Map<String, GdeltEvent> uniqueEvents = new LinkedHashMap<>();

        for (GdeltEvent event : events) {
            String signature = generateEventSignature(event);

            // Garde le plus récent ou celui avec plus de mentions
            if (!uniqueEvents.containsKey(signature) ||
                    shouldReplaceEvent(uniqueEvents.get(signature), event)) {
                uniqueEvents.put(signature, event);
            }
        }

        return new ArrayList<>(uniqueEvents.values());
    }

    /**
     * Enrichit les événements avec des métadonnées calculées
     */
    public List<GdeltEvent> enrichEvents(List<GdeltEvent> events) {
        return events.stream()
                .map(this::enrichEvent)
                .collect(Collectors.toList());
    }

    private boolean isValidEvent(GdeltEvent event) {
        // Validation de base
        if (event.getEventDate() == null) return false;
        if (event.getEventDate().isAfter(LocalDateTime.now().plusDays(1))) return false; // Pas d'événements futurs
        if (event.getEventDate().isBefore(LocalDateTime.now().minusYears(10))) return false; // Pas trop ancien

        return true;
    }

    private GdeltEvent cleanEvent(GdeltEvent event) {
        // Nettoyage des données
        if (event.getActor1Name() != null) {
            event.setActor1Name(cleanActorName(event.getActor1Name()));
        }

        if (event.getActor2Name() != null) {
            event.setActor2Name(cleanActorName(event.getActor2Name()));
        }

        // Normalisation des valeurs nulles
        if (event.getNumMentions() == null) {
            event.setNumMentions(1);
        }

        if (event.getGoldsteinScale() == null) {
            event.setGoldsteinScale(0.0);
        }

        return event;
    }

    private String cleanActorName(String actorName) {
        if (actorName == null) return null;

        return actorName.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("[^a-zA-Z0-9\\s\\-_]", "");
    }

    private String generateEventSignature(GdeltEvent event) {
        StringBuilder signature = new StringBuilder();

        if (event.getActor1Name() != null) {
            signature.append(event.getActor1Name());
        }
        signature.append("|");

        if (event.getActor2Name() != null) {
            signature.append(event.getActor2Name());
        }
        signature.append("|");

        if (event.getEventCode() != null) {
            signature.append(event.getEventCode());
        }
        signature.append("|");

        if (event.getEventDate() != null) {
            signature.append(event.getEventDate().toLocalDate());
        }

        return signature.toString();
    }

    private boolean shouldReplaceEvent(GdeltEvent existing, GdeltEvent candidate) {
        // Préférer l'événement avec plus de mentions
        int existingMentions = existing.getNumMentions() != null ? existing.getNumMentions() : 0;
        int candidateMentions = candidate.getNumMentions() != null ? candidate.getNumMentions() : 0;

        if (candidateMentions > existingMentions) return true;
        if (existingMentions > candidateMentions) return false;

        // En cas d'égalité, préférer le plus récent
        if (candidate.getEventDate() != null && existing.getEventDate() != null) {
            return candidate.getEventDate().isAfter(existing.getEventDate());
        }

        return false;
    }

    private GdeltEvent enrichEvent(GdeltEvent event) {
        // Ajout de métadonnées calculées
        // Par exemple, calculer l'impact basé sur mentions + Goldstein

        return event;
    }
}
