package org.arcos.Configuration;

import lombok.extern.slf4j.Slf4j;
import org.arcos.Personality.Values.ValueProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Initialise le profil de personnalité au démarrage Spring depuis PersonalityProperties.
 * Corrige le bug où ValueProfile.setProfile() n'était jamais appelé (profil toujours DEFAULT).
 */
@Component
@Slf4j
public class PersonalityInitializer {

    private final ValueProfile valueProfile;
    private final PersonalityProperties personalityProperties;

    @Autowired
    public PersonalityInitializer(ValueProfile valueProfile, PersonalityProperties personalityProperties) {
        this.valueProfile = valueProfile;
        this.personalityProperties = personalityProperties;
    }

    @PostConstruct
    public void initializeProfile() {
        String profileName = personalityProperties.getProfile();
        try {
            ValueProfile.PredefinedProfile profile = ValueProfile.PredefinedProfile.valueOf(profileName.toUpperCase());
            valueProfile.setProfile(profile);
            log.info("Profil de personnalité initialisé : {}", profile);
        } catch (IllegalArgumentException e) {
            log.warn("Profil de personnalité inconnu '{}', utilisation de DEFAULT.", profileName);
            valueProfile.setProfile(ValueProfile.PredefinedProfile.DEFAULT);
        }
    }
}
