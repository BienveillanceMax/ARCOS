package org.arcos.UnitTests.Tools;

import org.arcos.Tools.Actions.ActionResult;
import org.arcos.Tools.Actions.SearchActions;
import org.arcos.Tools.SearchTool.BraveSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import lombok.extern.slf4j.Slf4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@Slf4j
@ExtendWith(MockitoExtension.class)
class SearchActionsTest {

    @Mock
    private BraveSearchService searchService;

    private SearchActions searchActions;

    @BeforeEach
    void setUp() {
        searchActions = new SearchActions(searchService, 5);
    }

    @Test
    void searchTheWeb_WhenServiceNotAvailable_ShouldReturnFailureResult() {
        // Given
        when(searchService.isAvailable()).thenReturn(false);

        // When
        ActionResult result = searchActions.searchTheWeb("test query");

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("BRAVE_SEARCH_API_KEY");
    }

    @Test
    void searchTheWeb_WhenServiceNotAvailable_ShouldNotCallSearch() throws Exception {
        // Given
        when(searchService.isAvailable()).thenReturn(false);

        // When
        searchActions.searchTheWeb("test query");

        // Then : la méthode search ne doit jamais être appelée
        verify(searchService, never()).search(anyString(), any(BraveSearchService.SearchOptions.class));
    }

    @Test
    void searchTheWeb_WhenServiceNotAvailable_ShouldReturnZeroExecutionTime() {
        // Given
        when(searchService.isAvailable()).thenReturn(false);

        // When
        ActionResult result = searchActions.searchTheWeb("test query");

        // Then
        assertThat(result.getExecutionTimeMs()).isEqualTo(0);
    }
}
