package org.arcos.UnitTests.Boot;

import org.arcos.Boot.Greeting.PersonalityGreeting;
import org.arcos.Configuration.PersonalityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonalityGreetingTest {

    @Mock
    private PersonalityProperties personalityProperties;

    @InjectMocks
    private PersonalityGreeting personalityGreeting;

    @Test
    void generateMessage_Calcifer_NormalMode_ContainsExpectedText() {
        when(personalityProperties.getProfile()).thenReturn("CALCIFER");
        String message = personalityGreeting.generateMessage(false);
        assertTrue(message.contains("réveillé"), "Le message CALCIFER doit parler du réveil");
    }

    @Test
    void generateMessage_K2SO_NormalMode_ContainsExpectedText() {
        when(personalityProperties.getProfile()).thenReturn("K2SO");
        String message = personalityGreeting.generateMessage(false);
        assertTrue(message.contains("opérationnels"), "Le message K2SO doit mentionner les systèmes");
    }

    @Test
    void generateMessage_Glados_NormalMode_ContainsExpectedText() {
        when(personalityProperties.getProfile()).thenReturn("GLADOS");
        String message = personalityGreeting.generateMessage(false);
        assertTrue(message.contains("joie") || message.contains("Quelle"),
                "Le message GLADOS doit être sarcastique");
    }

    @Test
    void generateMessage_Default_NormalMode_ContainsBonjour() {
        when(personalityProperties.getProfile()).thenReturn("DEFAULT");
        String message = personalityGreeting.generateMessage(false);
        assertTrue(message.contains("Bonjour"), "Le message DEFAULT doit commencer par Bonjour");
    }

    @Test
    void generateMessage_DegradedMode_ContainsDegradedHint() {
        when(personalityProperties.getProfile()).thenReturn("CALCIFER");
        String message = personalityGreeting.generateMessage(true);
        assertTrue(message.contains("manque") || message.contains("trucs"),
                "Le message dégradé CALCIFER doit mentionner les services manquants");
    }

    @Test
    void generateMessage_NeverNull() {
        when(personalityProperties.getProfile()).thenReturn("DEFAULT");
        assertNotNull(personalityGreeting.generateMessage(false));
        assertNotNull(personalityGreeting.generateMessage(true));
    }
}
