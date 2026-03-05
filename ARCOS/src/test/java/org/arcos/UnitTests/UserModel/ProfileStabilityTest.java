package org.arcos.UnitTests.UserModel;

import org.arcos.UserModel.Models.ProfileStability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfileStabilityTest {

    @ParameterizedTest
    @CsvSource({
            "0, LOW",
            "1, LOW",
            "4, LOW",
            "5, MEDIUM",
            "7, MEDIUM",
            "9, MEDIUM",
            "10, HIGH",
            "15, HIGH",
            "100, HIGH"
    })
    void fromConversationCount_ShouldReturnCorrectStability(int count, String expected) {
        assertEquals(ProfileStability.valueOf(expected), ProfileStability.fromConversationCount(count));
    }

    @Test
    void fromConversationCount_BoundaryAt5_ShouldBeMedium() {
        assertEquals(ProfileStability.MEDIUM, ProfileStability.fromConversationCount(5));
    }

    @Test
    void fromConversationCount_BoundaryAt10_ShouldBeHigh() {
        assertEquals(ProfileStability.HIGH, ProfileStability.fromConversationCount(10));
    }
}
