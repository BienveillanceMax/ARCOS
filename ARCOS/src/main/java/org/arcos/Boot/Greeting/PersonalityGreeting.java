package org.arcos.Boot.Greeting;

import org.arcos.Configuration.PersonalityProperties;
import org.arcos.Setup.UI.AnsiPalette;
import org.arcos.Setup.UI.TerminalCapabilities;
import org.springframework.stereotype.Component;

/**
 * Message de bienvenue contextuel affiché après le rapport de boot.
 * Le ton s'adapte au profil de personnalité et à l'état des services.
 */
@Component
public class PersonalityGreeting {

    private final PersonalityProperties personalityProperties;

    public PersonalityGreeting(PersonalityProperties personalityProperties) {
        this.personalityProperties = personalityProperties;
    }

    /**
     * Génère le message de bienvenue.
     *
     * @param hasDegradedServices true si certains services sont OFFLINE ou DEGRADED
     * @return message formaté (avec couleur si TTY supporte l'ANSI)
     */
    public String generateMessage(boolean hasDegradedServices) {
        String profile = personalityProperties.getProfile();
        String message = buildMessage(profile, hasDegradedServices);
        return formatMessage(message);
    }

    private String buildMessage(String profile, boolean hasDegradedServices) {
        if (hasDegradedServices) {
            return buildDegradedMessage(profile);
        }
        return buildNormalMessage(profile);
    }

    private String buildNormalMessage(String profile) {
        switch (profile.toUpperCase()) {
            case "CALCIFER":
                return "<< Bon, je suis réveillé. Qu'est-ce que tu veux ? >>";
            case "K2SO":
                return "<< Tous les systèmes sont opérationnels. Probabilité de conversation productive : 47%. >>";
            case "GLADOS":
                return "<< Oh, vous voilà. Quelle... joie. Espérons que votre requête soit moins absurde que les précédentes. >>";
            default:
                return "<< Bonjour. Je suis prêt. >>";
        }
    }

    private String buildDegradedMessage(String profile) {
        switch (profile.toUpperCase()) {
            case "CALCIFER":
                return "<< Bon, je suis réveillé. Il me manque des trucs, mais on fera avec. >>";
            case "K2SO":
                return "<< Démarrage en mode dégradé. Capacités réduites. Je ferai de mon mieux avec ce que j'ai. >>";
            case "GLADOS":
                return "<< Certains composants sont... absents. Comme d'habitude, on s'en accommodera. >>";
            default:
                return "<< Bonjour. Certains services sont indisponibles, mais je reste opérationnel. >>";
        }
    }

    private String formatMessage(String message) {
        if (!TerminalCapabilities.isColorSupported()) {
            return "\n" + message + "\n";
        }
        return "\n" + AnsiPalette.ORANGE_BRIGHT + AnsiPalette.BOLD + message + AnsiPalette.RESET + "\n";
    }
}
