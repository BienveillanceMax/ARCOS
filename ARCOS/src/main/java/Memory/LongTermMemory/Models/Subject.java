package Memory.LongTermMemory.Models;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Énumération représentant le sujet d'un souvenir.
 * Permet de catégoriser les souvenirs selon leur nature.
 */
public enum Subject {

    /**
     * Souvenirs concernant l'IA elle-même (auto-réflexion, apprentissages personnels)
     */
    SELF("self"),

    /**
     * Souvenirs concernant l'utilisateur (préférences, habitudes, conversations)
     */
    USER("user"),

    /**
     * Souvenirs concernant le monde extérieur (actualités, faits, connaissances générales)
     */
    WORLD("world"),

    /**
     * Autres souvenirs qui ne rentrent pas dans les catégories précédentes
     */
    OTHER("other");

    private final String value;

    Subject(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Méthode pour désérialiser depuis JSON.
     */
    @JsonCreator
    public static Subject fromString(String value) {
        if (value == null) {
            return OTHER;
        }

        for (Subject subject : Subject.values()) {
            if (subject.value.equalsIgnoreCase(value)) {
                return subject;
            }
        }
        return OTHER;
    }

    @Override
    public String toString() {
        return value;
    }
}
