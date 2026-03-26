package org.arcos.Mood;

import org.arcos.Configuration.PersonalityProperties;
import org.arcos.Personality.Mood.Mood;
import org.arcos.Personality.Mood.MoodService;
import org.arcos.Personality.Mood.MoodStateHolder;
import org.arcos.Personality.Mood.MoodUpdate;
import org.arcos.Personality.Mood.PadState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class MoodServiceTest {

    @Mock
    private MoodStateHolder moodStateHolder;

    @Mock
    private PersonalityProperties personalityProperties;

    private MoodService moodService;

    @BeforeEach
    void setUp() {
        moodService = new MoodService(moodStateHolder, personalityProperties);
    }

    @Test
    void testApplyMoodUpdate() {
        // Given
        PadState initialState = new PadState(0.0, 0.0, 0.0);
        when(moodStateHolder.getPadState()).thenReturn(initialState);

        MoodUpdate update = new MoodUpdate();
        update.deltaPleasure = 0.5;
        update.deltaArousal = 0.2;
        update.deltaDominance = 0.1;
        update.reasoning = "Test reasoning";

        // When
        moodService.applyMoodUpdate(update);

        // Then
        verify(moodStateHolder).getPadState();
        verify(moodStateHolder).setPadState(argThat(state ->
            state.getPleasure() == 0.5 &&
            state.getArousal() == 0.2 &&
            state.getDominance() == 0.1
        ));
    }

    @Test
    void testGetCurrentMood() {
        when(moodStateHolder.getPadState()).thenReturn(new PadState(0.8, 0.6, 0.5));
        assertEquals(Mood.JOIE, moodService.getCurrentMood());
    }
}
