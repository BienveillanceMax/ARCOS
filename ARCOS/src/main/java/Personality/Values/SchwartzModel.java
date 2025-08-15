package Personality.Values;


import java.util.*;
import java.util.stream.Collectors;

public class SchwartzModel
{

    public enum DimensionSchwartz
    {
        OUVERTURE_AU_CHANGEMENT,
        AFFIRMATION_DE_SOI,
        DEPASSEMENT_DE_SOI,
        CONSERVATION
    }

    public enum ValeurSchwartz
    {
        AUTODETERMINATION_PENSEE("Autodétermination de la pensée", "Liberté de cultiver ses propres idées et capacités", DimensionSchwartz.OUVERTURE_AU_CHANGEMENT),
        AUTODETERMINATION_ACTIONS("Autodétermination des actions", "Liberté de déterminer ses propres actions", DimensionSchwartz.OUVERTURE_AU_CHANGEMENT),
        STIMULATION("Stimulation", "Excitation, nouveauté et changement", DimensionSchwartz.OUVERTURE_AU_CHANGEMENT),
        HEDONISME("Hédonisme", "Plaisir et gratification sensuelle", DimensionSchwartz.OUVERTURE_AU_CHANGEMENT),
        REALISATION("Réalisation", "Succès selon les normes sociales", DimensionSchwartz.AFFIRMATION_DE_SOI),
        POUVOIR_DOMINANCE("Pouvoir-dominance", "Pouvoir par l'exercice d'un contrôle sur les gens", DimensionSchwartz.AFFIRMATION_DE_SOI),
        POUVOIR_RESSOURCES("Pouvoir-ressources", "Pouvoir par le contrôle des ressources matérielles et sociales", DimensionSchwartz.AFFIRMATION_DE_SOI),
        IMAGE_PUBLIQUE("Image publique", "Sécurité et pouvoir en maintenant son image publique et en évitant l'humiliation", DimensionSchwartz.AFFIRMATION_DE_SOI),
        SECURITE_PERSONNEL("Sécurité-personnel", "Sécurité dans l'environnement immédiat", DimensionSchwartz.CONSERVATION),
        SECURITE_SOCIETE("Sécurité-société", "Sécurité et stabilité dans la société", DimensionSchwartz.CONSERVATION),
        TRADITION("Tradition", "Maintenir et préserver les traditions culturelles, familiales ou religieuses", DimensionSchwartz.CONSERVATION),
        CONFORMITE_REGLES("Conformité-règles", "Respect des règles, lois et obligations formelles", DimensionSchwartz.CONSERVATION),
        CONFORMITE_INTERPERSONNELLE("Conformité interpersonnelle", "Éviter de bouleverser ou de blesser les autres", DimensionSchwartz.CONSERVATION),
        HUMILITE("Humilité", "Reconnaître son insignifiance dans l'ensemble des choses", DimensionSchwartz.CONSERVATION),
        BIENVEILLANCE_SOINS("Bienveillance-soins", "Prendre soin du bien-être des membres du groupe d'appartenance", DimensionSchwartz.DEPASSEMENT_DE_SOI),
        BIENVEILLANCE_FIABILITE("Bienveillance-fiabilité", "Être un membre fiable et digne de confiance du groupe d'appartenance", DimensionSchwartz.DEPASSEMENT_DE_SOI),
        UNIVERSALISME_PREOCCUPATION("Universalisme-préoccupation", "Engagement envers l'égalité, la justice et la protection de tous", DimensionSchwartz.DEPASSEMENT_DE_SOI),
        UNIVERSALISME_NATURE("Universalisme-nature", "Préservation de l'environnement naturel", DimensionSchwartz.DEPASSEMENT_DE_SOI),
        UNIVERSALISME_TOLERANCE("Universalisme-tolérance", "Acceptation et compréhension de ceux qui sont différents de soi-même", DimensionSchwartz.DEPASSEMENT_DE_SOI);

        private final String libelle;
        private final String description;
        private final DimensionSchwartz dimension;
        private List<ValeurSchwartz> antagonistes;

        ValeurSchwartz(String libelle, String description, DimensionSchwartz dimension) {
            this.libelle = libelle;
            this.description = description;
            this.dimension = dimension;
        }

        public String getLibelle() {
            return libelle;
        }

        public String getDescription() {
            return description;
        }

        public DimensionSchwartz getDimension() {
            return dimension;
        }

        public List<ValeurSchwartz> getAntagonistes() {
            return antagonistes;
        }

        public static List<ValeurSchwartz> valuesOfDimension(DimensionSchwartz d) {
            return Arrays.stream(values()).filter(v -> v.dimension == d).collect(Collectors.toList());
        }

        private static void definirAntagonismes() {
            AUTODETERMINATION_PENSEE.antagonistes = List.of(SECURITE_PERSONNEL, SECURITE_SOCIETE, TRADITION, CONFORMITE_REGLES, CONFORMITE_INTERPERSONNELLE);
            AUTODETERMINATION_ACTIONS.antagonistes = AUTODETERMINATION_PENSEE.antagonistes;
            STIMULATION.antagonistes = List.of(SECURITE_PERSONNEL, SECURITE_SOCIETE, TRADITION, CONFORMITE_REGLES);
            HEDONISME.antagonistes = List.of(HUMILITE, UNIVERSALISME_PREOCCUPATION, UNIVERSALISME_TOLERANCE);
            REALISATION.antagonistes = List.of(HUMILITE, BIENVEILLANCE_SOINS, BIENVEILLANCE_FIABILITE);
            POUVOIR_DOMINANCE.antagonistes = List.of(HUMILITE, UNIVERSALISME_PREOCCUPATION, UNIVERSALISME_TOLERANCE);
            POUVOIR_RESSOURCES.antagonistes = POUVOIR_DOMINANCE.antagonistes;
            IMAGE_PUBLIQUE.antagonistes = List.of(HUMILITE, AUTODETERMINATION_PENSEE);
            SECURITE_PERSONNEL.antagonistes = List.of(STIMULATION, AUTODETERMINATION_ACTIONS);
            SECURITE_SOCIETE.antagonistes = SECURITE_PERSONNEL.antagonistes;
            TRADITION.antagonistes = List.of(AUTODETERMINATION_PENSEE, STIMULATION);
            CONFORMITE_REGLES.antagonistes = AUTODETERMINATION_ACTIONS.antagonistes;
            CONFORMITE_INTERPERSONNELLE.antagonistes = List.of(STIMULATION, HEDONISME);
            HUMILITE.antagonistes = List.of(HEDONISME, REALISATION, POUVOIR_DOMINANCE);
            BIENVEILLANCE_SOINS.antagonistes = List.of(POUVOIR_DOMINANCE, POUVOIR_RESSOURCES);
            BIENVEILLANCE_FIABILITE.antagonistes = BIENVEILLANCE_SOINS.antagonistes;
            UNIVERSALISME_PREOCCUPATION.antagonistes = List.of(POUVOIR_DOMINANCE, POUVOIR_RESSOURCES);
            UNIVERSALISME_NATURE.antagonistes = List.of(POUVOIR_RESSOURCES);
            UNIVERSALISME_TOLERANCE.antagonistes = POUVOIR_DOMINANCE.antagonistes;
        }

        static {
            definirAntagonismes();
        }
    }

    public static class ProfilValeurs
    {
        private final EnumMap<ValeurSchwartz, Double> scores = new EnumMap<>(ValeurSchwartz.class);

        public ProfilValeurs() {
            for (ValeurSchwartz v : ValeurSchwartz.values()) scores.put(v, 50.0);
        }

        public void setScore(ValeurSchwartz valeur, double score) {
            if (score < 0 || score > 100) throw new IllegalArgumentException("Score entre 0 et 100");
            scores.put(valeur, score);
        }

        public double getScore(ValeurSchwartz valeur) {
            return scores.getOrDefault(valeur, 0.0);
        }

        public double moyenneParDimension(DimensionSchwartz dimension) {
            return ValeurSchwartz.valuesOfDimension(dimension).stream().mapToDouble(this::getScore).average().orElse(0.0);
        }

        public EnumMap<DimensionSchwartz, Double> moyenneParDimension() {
            EnumMap<DimensionSchwartz, Double> res = new EnumMap<>(DimensionSchwartz.class);
            for (DimensionSchwartz d : DimensionSchwartz.values()) res.put(d, moyenneParDimension(d));
            return res;
        }

        public Map<ValeurSchwartz, Double> normaliseSommeUn() {
            double sum = scores.values().stream().mapToDouble(Double::doubleValue).sum();
            double uniform = 1.0 / ValeurSchwartz.values().length;
            if (sum == 0)
                return Arrays.stream(ValeurSchwartz.values()).collect(Collectors.toMap(v -> v, v -> uniform, (a, b) -> a, () -> new EnumMap<>(ValeurSchwartz.class)));
            return scores.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue() / sum, (a, b) -> a, () -> new EnumMap<>(ValeurSchwartz.class)));
        }

        public List<ValeurSchwartz> valeursEnConflit(ValeurSchwartz valeur) {
            List<ValeurSchwartz> antagonistes = valeur.getAntagonistes();
            if (antagonistes == null) return List.of();
            return antagonistes.stream().filter(v -> getScore(v) > 60).collect(Collectors.toList());
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("Profil de valeurs:\n");
            for (Map.Entry<ValeurSchwartz, Double> e : scores.entrySet()) {
                sb.append(String.format("%-30s : %6.2f\n", e.getKey().getLibelle(), e.getValue()));
            }
            sb.append("\nMoyennes par dimension:\n");
            moyenneParDimension().forEach((d, m) -> sb.append(String.format("%-25s : %6.2f\n", d.name(), m)));
            return sb.toString();
        }
    }
}