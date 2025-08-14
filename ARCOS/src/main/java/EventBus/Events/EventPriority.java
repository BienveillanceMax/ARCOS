package EventBus.Events;

/**
 * Énumération des priorités d'événements
 * Ordre: HIGH > MEDIUM > LOW
 */
public enum EventPriority {
    HIGH(3, "Priorité haute - traitement immédiat"),
    MEDIUM(2, "Priorité moyenne - traitement normal"),
    LOW(1, "Priorité basse - traitement différé");

    private final int level;
    private final String description;

    EventPriority(int level, String description) {
        this.level = level;
        this.description = description;
    }

    public int getLevel() {
        return level;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return name() + " (" + description + ")";
    }
}
