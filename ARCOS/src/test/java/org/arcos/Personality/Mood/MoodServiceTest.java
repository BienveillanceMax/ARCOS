package org.arcos.Personality.Mood;

import org.arcos.Memory.ConversationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class MoodServiceTest {

    @Mock
    private ConversationContext context;

    @InjectMocks
    private MoodService moodService;

    @Test
    void testApplyMoodUpdate() {
        // Given
        PadState initialState = new PadState(0.0, 0.0, 0.0);
        when(context.getPadState()).thenReturn(initialState);

        MoodUpdate update = new MoodUpdate();
        update.deltaPleasure = 0.5;
        update.deltaArousal = 0.2;
        update.deltaDominance = 0.1;
        update.reasoning = "Test reasoning";

        // When
        moodService.applyMoodUpdate(update);

        // Then
        verify(context).getPadState();
        verify(context).setPadState(argThat(state ->
            state.getPleasure() == 0.5 &&
            state.getArousal() == 0.2 &&
            state.getDominance() == 0.1
        ));
    }

    @Test
    void testGetCurrentMood() {
        when(context.getPadState()).thenReturn(new PadState(0.8, 0.6, 0.5));
        assertEquals(Mood.JOIE, moodService.getCurrentMood());
    }
}
