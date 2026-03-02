package org.arcos.UnitTests.Tools;

import org.arcos.Memory.LongTermMemory.Models.DesireEntry;
import org.arcos.Memory.LongTermMemory.Models.MemoryEntry;
import org.arcos.Memory.LongTermMemory.Models.OpinionEntry;
import org.arcos.Memory.LongTermMemory.service.MemoryService;
import org.arcos.Personality.Desires.DesireService;
import org.arcos.Personality.Opinions.OpinionService;
import org.arcos.Tools.Actions.ActionResult;
import org.arcos.Tools.Actions.MemoryActions;
import org.arcos.common.utils.ObjectCreationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemoryActionsTest {

    @Mock
    private MemoryService memoryService;

    @Mock
    private OpinionService opinionService;

    @Mock
    private DesireService desireService;

    private MemoryActions memoryActions;

    @BeforeEach
    void setUp() {
        memoryActions = new MemoryActions(memoryService, opinionService, desireService);
    }

    @Test
    void searchMemory_WithSouvenirType_ShouldDelegateToMemoryService() {
        // Given
        MemoryEntry memory = ObjectCreationUtils.createMemoryEntry();
        when(memoryService.searchMemories("test query", 5)).thenReturn(List.of(memory));

        // When
        ActionResult result = memoryActions.searchMemory("test query", "SOUVENIR");

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("1 résultat(s)");
        verify(memoryService).searchMemories("test query", 5);
        verify(opinionService, never()).searchOpinions(anyString());
        verify(desireService, never()).getPendingDesires();
    }

    @Test
    void searchMemory_WithOpinionType_ShouldDelegateToOpinionService() {
        // Given
        OpinionEntry opinion = ObjectCreationUtils.createOpinionEntry();
        when(opinionService.searchOpinions("test query")).thenReturn(List.of(opinion));

        // When
        ActionResult result = memoryActions.searchMemory("test query", "OPINION");

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("1 résultat(s)");
        verify(opinionService).searchOpinions("test query");
        verify(memoryService, never()).searchMemories(anyString(), anyInt());
    }

    @Test
    void searchMemory_WithDesirType_ShouldDelegateToDesireService() {
        // Given
        DesireEntry desire = ObjectCreationUtils.createIntensePendingDesireEntry("opinion-1");
        when(desireService.getPendingDesires()).thenReturn(List.of(desire));

        // When
        ActionResult result = memoryActions.searchMemory("test query", "DESIR");

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("1 résultat(s)");
        verify(desireService).getPendingDesires();
        verify(memoryService, never()).searchMemories(anyString(), anyInt());
    }

    @Test
    void searchMemory_WithNullType_ShouldDefaultToSouvenir() {
        // Given
        when(memoryService.searchMemories("test query", 5)).thenReturn(Collections.emptyList());

        // When
        ActionResult result = memoryActions.searchMemory("test query", null);

        // Then
        assertThat(result.isSuccess()).isTrue();
        verify(memoryService).searchMemories("test query", 5);
    }

    @Test
    void searchMemory_WhenNoResults_ShouldReturnSuccessWithMessage() {
        // Given
        when(memoryService.searchMemories("unknown", 5)).thenReturn(Collections.emptyList());

        // When
        ActionResult result = memoryActions.searchMemory("unknown", "SOUVENIR");

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Aucun résultat trouvé.");
    }
}
