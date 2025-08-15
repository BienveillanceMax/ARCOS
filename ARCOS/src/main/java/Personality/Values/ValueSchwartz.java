package Personality.Values;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum ValueSchwartz
{SELF_DIRECTION_THOUGHT("Autodétermination de la pensée", "Liberté de cultiver ses propres idées et capacités", DimensionSchwartz.OPENNESS_TO_CHANGE),
    SELF_DIRECTION_ACTION("Autodétermination des actions", "Liberté de déterminer ses propres actions", DimensionSchwartz.OPENNESS_TO_CHANGE),
    STIMULATION("Stimulation", "Excitation, nouveauté et changement", DimensionSchwartz.OPENNESS_TO_CHANGE),
    HEDONISM("Hédonisme", "Plaisir et gratification sensuelle", DimensionSchwartz.OPENNESS_TO_CHANGE),
    ACHIEVEMENT("Réalisation", "Succès selon les normes sociales", DimensionSchwartz.SELF_ENHANCEMENT),
    POWER_DOMINANCE("Pouvoir-dominance", "Pouvoir par l'exercice d'un contrôle sur les gens", DimensionSchwartz.SELF_ENHANCEMENT),
    POWER_RESOURCES("Pouvoir-ressources", "Pouvoir par le contrôle des ressources matérielles et sociales", DimensionSchwartz.SELF_ENHANCEMENT),
    FACE("Image publique", "Sécurité et pouvoir en maintenant son image publique et en évitant l'humiliation", DimensionSchwartz.SELF_ENHANCEMENT),
    SECURITY_PERSONAL("Sécurité-personnel", "Sécurité dans l'environnement immédiat", DimensionSchwartz.CONSERVATION),
    SECURITY_SOCIAL("Sécurité-société", "Sécurité et stabilité dans la société", DimensionSchwartz.CONSERVATION),
    TRADITION("Tradition", "Maintenir et préserver les traditions culturelles, familiales ou religieuses", DimensionSchwartz.CONSERVATION),
    CONFORMITY_RULES("Conformité-règles", "Respect des règles, lois et obligations formelles", DimensionSchwartz.CONSERVATION),
    CONFORMITY_INTERPERSONAL("Conformité interpersonnelle", "Éviter de bouleverser ou de blesser les autres", DimensionSchwartz.CONSERVATION),
    HUMILITY("Humilité", "Reconnaître son insignifiance dans l'ensemble des choses", DimensionSchwartz.CONSERVATION),
    BENEVOLENCE_CARE("Bienveillance-soins", "Prendre soin du bien-être des membres du groupe d'appartenance", DimensionSchwartz.SELF_TRANSCENDENCE),
    BENEVOLENCE_DEPENDABILITY("Bienveillance-fiabilité", "Être un membre fiable et digne de confiance du groupe d'appartenance", DimensionSchwartz.SELF_TRANSCENDENCE),
    UNIVERSALISM_CONCERN("Universalisme-préoccupation", "Engagement envers l'égalité, la justice et la protection de tous", DimensionSchwartz.SELF_TRANSCENDENCE),
    UNIVERSALISM_NATURE("Universalisme-nature", "Préservation de l'environnement naturel", DimensionSchwartz.SELF_TRANSCENDENCE),
    UNIVERSALISM_TOLERANCE("Universalisme-tolérance", "Acceptation et compréhension de ceux qui sont différents de soi-même", DimensionSchwartz.SELF_TRANSCENDENCE);

    private final String label;
    private final String description;
    private final DimensionSchwartz dimension;
    private List<ValueSchwartz> antagonists;

    ValueSchwartz(String label, String description, DimensionSchwartz dimension) {
        this.label = label;
        this.description = description;
        this.dimension = dimension;
    }

    public String getLabel() { return label; }
    public String getDescription() { return description; }
    public DimensionSchwartz getDimension() { return dimension; }
    public List<ValueSchwartz> getAntagonists() { return antagonists; }

    public static List<ValueSchwartz> valuesOfDimension(DimensionSchwartz d) {
        return Arrays.stream(values()).filter(v -> v.dimension == d).collect(Collectors.toList());
    }

    private static void defineAntagonists() {
        SELF_DIRECTION_THOUGHT.antagonists = List.of(SECURITY_PERSONAL, SECURITY_SOCIAL, TRADITION, CONFORMITY_RULES, CONFORMITY_INTERPERSONAL);
        SELF_DIRECTION_ACTION.antagonists = SELF_DIRECTION_THOUGHT.antagonists;
        STIMULATION.antagonists = List.of(SECURITY_PERSONAL, SECURITY_SOCIAL, TRADITION, CONFORMITY_RULES);
        HEDONISM.antagonists = List.of(HUMILITY, UNIVERSALISM_CONCERN, UNIVERSALISM_TOLERANCE);
        ACHIEVEMENT.antagonists = List.of(HUMILITY, BENEVOLENCE_CARE, BENEVOLENCE_DEPENDABILITY);
        POWER_DOMINANCE.antagonists = List.of(HUMILITY, UNIVERSALISM_CONCERN, UNIVERSALISM_TOLERANCE);
        POWER_RESOURCES.antagonists = POWER_DOMINANCE.antagonists;
        FACE.antagonists = List.of(HUMILITY, SELF_DIRECTION_THOUGHT);
        SECURITY_PERSONAL.antagonists = List.of(STIMULATION, SELF_DIRECTION_ACTION);
        SECURITY_SOCIAL.antagonists = SECURITY_PERSONAL.antagonists;
        TRADITION.antagonists = List.of(SELF_DIRECTION_THOUGHT, STIMULATION);
        CONFORMITY_RULES.antagonists = SELF_DIRECTION_ACTION.antagonists;
        CONFORMITY_INTERPERSONAL.antagonists = List.of(STIMULATION, HEDONISM);
        HUMILITY.antagonists = List.of(HEDONISM, ACHIEVEMENT, POWER_DOMINANCE);
        BENEVOLENCE_CARE.antagonists = List.of(POWER_DOMINANCE, POWER_RESOURCES);
        BENEVOLENCE_DEPENDABILITY.antagonists = BENEVOLENCE_CARE.antagonists;
        UNIVERSALISM_CONCERN.antagonists = List.of(POWER_DOMINANCE, POWER_RESOURCES);
        UNIVERSALISM_NATURE.antagonists = List.of(POWER_RESOURCES);
        UNIVERSALISM_TOLERANCE.antagonists = POWER_DOMINANCE.antagonists;
    }

    static {
        defineAntagonists();
    }
}

