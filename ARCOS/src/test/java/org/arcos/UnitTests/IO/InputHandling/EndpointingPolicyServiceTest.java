package org.arcos.UnitTests.IO.InputHandling;

import org.arcos.Configuration.AudioProperties;
import org.arcos.IO.InputHandling.EndpointingPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EndpointingPolicyServiceTest {

    private EndpointingPolicyService policy;

    @BeforeEach
    void setUp() {
        AudioProperties audio = new AudioProperties();
        audio.setConversationSilenceMs(500);
        policy = new EndpointingPolicyService(audio);
    }

    @Test
    void endsWithQuestion_ShouldDetectTrailingQuestionMark() {
        // Given / When / Then : toute réplique finissant par "?" est une question, quel qu'en soit le type
        assertThat(policy.endsWithQuestion("Veux-tu que je le fasse ?")).isTrue();
        assertThat(policy.endsWithQuestion("Comment veux-tu organiser ça ?")).isTrue();
        assertThat(policy.endsWithQuestion("Bien. Tu confirmes ?")).isTrue();
        // Espaces en fin ignorés (strip)
        assertThat(policy.endsWithQuestion("Tu préfères quoi ?  ")).isTrue();
    }

    @Test
    void endsWithQuestion_ShouldBeFalseForStatementsAndEdgeCases() {
        assertThat(policy.endsWithQuestion("Le rendez-vous est ajouté.")).isFalse();
        assertThat(policy.endsWithQuestion(null)).isFalse();
        assertThat(policy.endsWithQuestion("  ")).isFalse();
        // Une question en milieu de réponse ne compte pas : seule la fin décide
        assertThat(policy.endsWithQuestion("Comment ? Je plaisante. C'est fait.")).isFalse();
    }

    @Test
    void conversationSilenceMs_ShouldLengthenAfterQuestion_AndNeverShorten() {
        // Given : base 500ms
        // When / Then : question → 650 (x1.3), affirmation → 500 (inchangé, jamais raccourci)
        assertThat(policy.conversationSilenceMs("Veux-tu continuer ?")).isEqualTo(650);
        assertThat(policy.conversationSilenceMs("Comment veux-tu continuer ?")).isEqualTo(650);
        assertThat(policy.conversationSilenceMs("C'est fait.")).isEqualTo(500);
        assertThat(policy.conversationSilenceMs(null)).isEqualTo(500);
    }
}
