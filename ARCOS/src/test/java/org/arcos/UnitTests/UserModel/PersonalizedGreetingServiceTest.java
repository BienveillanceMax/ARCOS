package org.arcos.UnitTests.UserModel;

import org.arcos.UserModel.Engagement.EngagementRecord;
import org.arcos.UserModel.Greeting.PersonalizedGreetingService;
import org.arcos.UserModel.Models.TreeBranch;
import org.arcos.UserModel.UserModelProperties;
import org.arcos.UserModel.UserObservationTree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PersonalizedGreetingServiceTest {

    private UserObservationTree tree;
    private PersonalizedGreetingService service;

    @BeforeEach
    void setUp() {
        tree = new UserObservationTree(new UserModelProperties());
        service = new PersonalizedGreetingService(tree);
    }

    // ---- Cold start gate ----

    @Test
    void buildGreetingContext_ReturnsEmpty_WhenTooFewConversations() {
        tree.setConversationCount(1);
        assertTrue(service.buildGreetingContext().isEmpty());
    }

    // ---- Time of day ----

    @Test
    void buildGreetingContext_IncludesMorning_BeforeNoon() {
        tree.setConversationCount(5);
        Optional<String> result = service.buildGreetingContext(LocalTime.of(9, 0), Instant.now());
        assertTrue(result.isPresent());
        assertTrue(result.get().contains("matin"));
    }

    @Test
    void buildGreetingContext_IncludesAfternoon_Between12And18() {
        tree.setConversationCount(5);
        Optional<String> result = service.buildGreetingContext(LocalTime.of(14, 0), Instant.now());
        assertTrue(result.isPresent());
        assertTrue(result.get().contains("après-midi"));
    }

    @Test
    void buildGreetingContext_IncludesEvening_After18() {
        tree.setConversationCount(5);
        Optional<String> result = service.buildGreetingContext(LocalTime.of(21, 0), Instant.now());
        assertTrue(result.isPresent());
        assertTrue(result.get().contains("soir"));
    }

    // ---- Days since last conversation ----

    @Test
    void buildGreetingContext_IncludesDaysSinceLastConversation_WhenAbsent() {
        tree.setConversationCount(5);
        Instant now = Instant.now();
        tree.addEngagementRecord(new EngagementRecord(now.minus(5, ChronoUnit.DAYS), 3));

        Optional<String> result = service.buildGreetingContext(LocalTime.of(10, 0), now);

        assertTrue(result.isPresent());
        assertTrue(result.get().contains("5 jours"), "Got: " + result.get());
    }

    @Test
    void buildGreetingContext_OmitsDaysSince_WhenRecent() {
        tree.setConversationCount(5);
        Instant now = Instant.now();
        tree.addEngagementRecord(new EngagementRecord(now.minus(12, ChronoUnit.HOURS), 3));

        Optional<String> result = service.buildGreetingContext(LocalTime.of(10, 0), now);

        assertTrue(result.isPresent());
        assertFalse(result.get().contains("jours"), "Should not mention days since for recent conversations");
    }

    // ---- Branch summaries ----

    @Test
    void buildGreetingContext_IncludesObjectifsSummary() {
        tree.setConversationCount(5);
        tree.setSummary(TreeBranch.OBJECTIFS, "Apprendre Rust");

        Optional<String> result = service.buildGreetingContext(LocalTime.of(10, 0), Instant.now());

        assertTrue(result.isPresent());
        assertTrue(result.get().contains("Apprendre Rust"));
        assertTrue(result.get().contains("Objectifs actuels"));
    }

    @Test
    void buildGreetingContext_IncludesHabitudesSummary() {
        tree.setConversationCount(5);
        tree.setSummary(TreeBranch.HABITUDES, "Se lève tôt");

        Optional<String> result = service.buildGreetingContext(LocalTime.of(10, 0), Instant.now());

        assertTrue(result.isPresent());
        assertTrue(result.get().contains("Se lève tôt"));
        assertTrue(result.get().contains("Habitudes connues"));
    }

    @Test
    void buildGreetingContext_IncludesIdentitySummary() {
        tree.setConversationCount(5);
        tree.setSummary(TreeBranch.IDENTITE, "Pierre, développeur Java");

        Optional<String> result = service.buildGreetingContext(LocalTime.of(10, 0), Instant.now());

        assertTrue(result.isPresent());
        assertTrue(result.get().contains("Pierre, développeur Java"));
        assertTrue(result.get().contains("Identité"));
    }

    // ---- Combined ----

    @Test
    void buildGreetingContext_CombinesAllElements() {
        tree.setConversationCount(10);
        Instant now = Instant.now();
        tree.addEngagementRecord(new EngagementRecord(now.minus(3, ChronoUnit.DAYS), 5));
        tree.setSummary(TreeBranch.IDENTITE, "Pierre");
        tree.setSummary(TreeBranch.OBJECTIFS, "Finir ARCOS Phase 3");

        Optional<String> result = service.buildGreetingContext(LocalTime.of(8, 30), now);

        assertTrue(result.isPresent());
        String ctx = result.get();
        assertTrue(ctx.contains("matin"));
        assertTrue(ctx.contains("3 jours"));
        assertTrue(ctx.contains("Pierre"));
        assertTrue(ctx.contains("Finir ARCOS Phase 3"));
    }
}
