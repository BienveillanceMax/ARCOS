package org.arcos.UnitTests.IO.InputHandling;

import org.arcos.Configuration.AudioProperties;
import org.arcos.IO.InputHandling.EndpointingPolicyService;
import org.arcos.IO.InputHandling.EndpointingPolicyService.ResponseContextHint;
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
    void classify_YesNoQuestions_ShouldBeYesNo() {
        // Given / When / Then : inversion, est-ce que, intonation — sans mot interrogatif ouvert
        assertThat(policy.classify("Veux-tu que je le fasse ?")).isEqualTo(ResponseContextHint.YES_NO_QUESTION);
        assertThat(policy.classify("Est-ce que tu as fini ?")).isEqualTo(ResponseContextHint.YES_NO_QUESTION);
        assertThat(policy.classify("Bien. Tu confirmes ?")).isEqualTo(ResponseContextHint.YES_NO_QUESTION);
    }

    @Test
    void classify_OpenQuestions_ShouldBeOpen() {
        assertThat(policy.classify("Comment veux-tu organiser ça ?")).isEqualTo(ResponseContextHint.OPEN_QUESTION);
        assertThat(policy.classify("Qu'est-ce que tu en penses ?")).isEqualTo(ResponseContextHint.OPEN_QUESTION);
        assertThat(policy.classify("Tu préfères quoi ?")).isEqualTo(ResponseContextHint.OPEN_QUESTION);
        assertThat(policy.classify("C'est noté. Pourquoi cette date ?")).isEqualTo(ResponseContextHint.OPEN_QUESTION);
    }

    @Test
    void classify_StatementsAndEdgeCases_ShouldBeStatement() {
        assertThat(policy.classify("Le rendez-vous est ajouté.")).isEqualTo(ResponseContextHint.STATEMENT);
        assertThat(policy.classify(null)).isEqualTo(ResponseContextHint.STATEMENT);
        assertThat(policy.classify("  ")).isEqualTo(ResponseContextHint.STATEMENT);
        // La question ouverte en milieu de réponse ne compte pas : seule la dernière phrase décide
        assertThat(policy.classify("Comment ? Je plaisante. C'est fait.")).isEqualTo(ResponseContextHint.STATEMENT);
    }

    @Test
    void conversationSilenceMs_ShouldScaleWithHint() {
        // Given : base 500ms
        // When / Then : fermée → 350, ouverte → 650, affirmation → 500
        assertThat(policy.conversationSilenceMs("Veux-tu continuer ?")).isEqualTo(350);
        assertThat(policy.conversationSilenceMs("Comment veux-tu continuer ?")).isEqualTo(650);
        assertThat(policy.conversationSilenceMs("C'est fait.")).isEqualTo(500);
    }
}
