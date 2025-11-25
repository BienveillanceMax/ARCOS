package EventBus.Events;

import Memory.LongTermMemory.Models.DesireEntry;
import Tools.CalendarTool.CalendarEvent;

/**
 * Énumération des différents types d'événements supportés par le système
 */
public enum EventType
{
    WAKEWORD("Détection de mot-clé de réveil", String.class),
    TIMER("Événement de minuterie", String.class),
    ALERT("Alerte système", String.class),
    USER_COMMAND("Commande utilisateur", String.class),
    SYSTEM_STATUS("Changement de statut système", String.class),
    AUDIO_INPUT("Entrée audio", byte[].class),
    AUDIO_OUTPUT("Sortie audio", byte[].class),
    NETWORK_EVENT("Événement réseau", String.class),
    ERROR("Erreur système", String.class),
    NOTIFICATION("Notification", String.class),
    DEVICE_CONNECTED("Périphérique connecté", String.class),
    DEVICE_DISCONNECTED("Périphérique déconnecté", String.class),
    INITIATIVE("Initiative", DesireEntry.class),
    CALENDAR_EVENT_SCHEDULER("Événement de calendrier", CalendarEvent.class);

    private final String description;
    private final Class<?> payloadType;

    EventType(String description, Class<?> payloadType) {
        this.description = description;
        this.payloadType = payloadType;
    }

    public String getDescription() {
        return description;
    }

    public Class<?> getPayloadType() {
        return payloadType;
    }

    @Override
    public String toString() {
        return name() + " (" + description + ")";
    }
}

