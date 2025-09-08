package EventBus.Events;

/**
 * Énumération des différents types d'événements supportés par le système
 */
public enum EventType
{
    WAKEWORD("Détection de mot-clé de réveil"),
    TIMER("Événement de minuterie"),
    ALERT("Alerte système"),
    USER_COMMAND("Commande utilisateur"),
    SYSTEM_STATUS("Changement de statut système"),
    AUDIO_INPUT("Entrée audio"),
    AUDIO_OUTPUT("Sortie audio"),
    NETWORK_EVENT("Événement réseau"),
    ERROR("Erreur système"),
    NOTIFICATION("Notification"),
    DEVICE_CONNECTED("Périphérique connecté"),
    DEVICE_DISCONNECTED("Périphérique déconnecté"),
    INITIATIVE("Initiative"),
    CALENDAR_EVENT_SCHEDULER("Événement de calendrier");

    private final String description;

    EventType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return name() + " (" + description + ")";
    }
}

