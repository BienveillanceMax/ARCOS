package org.arcos.IntegrationTests.UserModel;

import org.arcos.UserModel.Models.*;
import org.arcos.UserModel.UserModelProperties;
import org.arcos.UserModel.UserObservationTree;
import org.arcos.UserModel.UserModelAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class UserModelDisabledIT {

    @Test
    void whenUserModelDisabled_ConditionalOnPropertyShouldPreventBeanCreation() {
        // Verify that @ConditionalOnProperty works correctly
        MockEnvironment env = new MockEnvironment();
        env.setProperty("arcos.user-model.enabled", "false");

        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.setEnvironment(env);
        ctx.register(UserModelAutoConfiguration.class);
        ctx.refresh();

        assertFalse(ctx.containsBean("userObservationTree"),
                "UserObservationTree should not be created when user-model.enabled=false");
        assertFalse(ctx.containsBean("localEmbeddingService"),
                "LocalEmbeddingService should not be created when user-model.enabled=false");

        ctx.close();
    }

    @Test
    void whenUserModelEnabled_BeansShouldBeCreatedByDefault() {
        // Verify that matchIfMissing=true means enabled by default
        ConditionalOnProperty annotation = UserModelAutoConfiguration.class
                .getAnnotation(ConditionalOnProperty.class);
        assertNotNull(annotation);
        assertEquals("arcos.user-model.enabled", annotation.name()[0]);
        assertEquals("true", annotation.havingValue());
        assertTrue(annotation.matchIfMissing());
    }

    @Test
    void propertiesShouldHaveCorrectDefaults() {
        UserModelProperties defaults = new UserModelProperties();
        assertTrue(defaults.isEnabled());
        assertEquals(300, defaults.getMaxActiveObservations());
        assertEquals(3, defaults.getMinConversationsBeforeInjection());
        assertEquals(80, defaults.getTotalBudgetTokens());
        assertEquals(25, defaults.getIdentityBudgetTokens());
        assertEquals(30, defaults.getCommunicationBudgetTokens());
        assertEquals(0.3, defaults.getEmaAlphaColdStart());
        assertEquals(0.1, defaults.getEmaAlphaStable());
        assertEquals(0.20, defaults.getSignificanceThreshold());
        assertEquals(3, defaults.getSignificanceConsecutiveSessions());
        assertEquals(500, defaults.getDebounceSaveMs());
        assertEquals(0.3, defaults.getRetrievalMinSrank());
        assertNotNull(defaults.getDisfluenceWords());
        assertFalse(defaults.getDisfluenceWords().isEmpty());
        assertEquals("data/user-tree.json", defaults.getStoragePath());
        assertEquals("data/user-tree-archive.json", defaults.getArchivePath());
        assertEquals("all-MiniLM-L6-v2", defaults.getEmbeddingModelName());
        assertEquals("0 0 3 * * *", defaults.getPruningCron());
    }

    @Test
    void userProfileContext_isEmpty_ShouldWorkCorrectly() {
        assertTrue(new UserProfileContext(null, null, null, 0).isEmpty());
        assertTrue(new UserProfileContext("", "", "", 5).isEmpty());
        assertFalse(new UserProfileContext("Pierre", null, null, 5).isEmpty());
        assertFalse(new UserProfileContext(null, "Concis", null, 5).isEmpty());
        assertFalse(new UserProfileContext(null, null, "Leaf text", 5).isEmpty());
    }

    @Test
    void observationCandidate_fromDto_ShouldMapCorrectly() {
        ObservationCandidateDto dto = new ObservationCandidateDto(
                "Mon créateur s'appelle Pierre", "IDENTITE", null, true);

        ObservationCandidate candidate = ObservationCandidate.fromDto(dto);

        assertEquals("Mon créateur s'appelle Pierre", candidate.text());
        assertEquals(TreeBranch.IDENTITE, candidate.branch());
        assertNull(candidate.replacesText());
        assertTrue(candidate.explicit());
        assertEquals(0.8f, candidate.emotionalImportance(), 0.01f);
    }

    @Test
    void observationCandidate_fromDto_InvalidBranch_ShouldDefaultToInterets() {
        ObservationCandidateDto dto = new ObservationCandidateDto(
                "Some text", "UNKNOWN_BRANCH", null, false);

        ObservationCandidate candidate = ObservationCandidate.fromDto(dto);

        assertEquals(TreeBranch.INTERETS, candidate.branch());
    }
}
