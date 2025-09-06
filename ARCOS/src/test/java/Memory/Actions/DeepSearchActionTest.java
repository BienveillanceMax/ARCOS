package Memory.Actions;

import Exceptions.SearchException;
import Memory.Actions.Entities.ActionResult;
import Memory.Actions.Entities.Actions.DeepSearchAction;
import Tools.SearchTool.BraveSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DeepSearchActionTest {

    @Mock
    private BraveSearchService braveSearchService;

    private DeepSearchAction deepSearchAction;

    @BeforeEach
    void setUp() {
        deepSearchAction = new DeepSearchAction(braveSearchService);
    }

    @Test
    void testExecuteSuccess() throws SearchException {
        // Arrange
        String query = "test query";
        Map<String, Object> params = new HashMap<>();
        params.put("query", query);

        BraveSearchService.SearchResultItem item = new BraveSearchService.SearchResultItem(
                "Test Title", "http://example.com", "Test Description", "2025-01-01");
        item.setExtractedContent("This is the extracted content.");

        BraveSearchService.SearchResult searchResult = new BraveSearchService.SearchResult(
                query, Collections.singletonList(item), 1);

        when(braveSearchService.searchAndExtractContent(query)).thenReturn(searchResult);

        // Act
        ActionResult result = deepSearchAction.execute(params);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals("Recherche approfondie effectuée avec succès", result.getMessage());
        List<String> results = (List<String>) result.getData();
        assertNotNull(results);
        assertFalse(results.isEmpty());
        assertTrue(results.get(2).contains("This is the extracted content."));
    }

    @Test
    void testExecuteSearchException() throws SearchException {
        // Arrange
        String query = "test query";
        Map<String, Object> params = new HashMap<>();
        params.put("query", query);

        when(braveSearchService.searchAndExtractContent(query)).thenThrow(new SearchException("Brave API error"));

        // Act
        ActionResult result = deepSearchAction.execute(params);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("Erreur de Recherche: Brave API error", result.getMessage());
    }

    @Test
    void testExecuteNoResults() throws SearchException {
        // Arrange
        String query = "no results query";
        Map<String, Object> params = new HashMap<>();
        params.put("query", query);

        BraveSearchService.SearchResult searchResult = new BraveSearchService.SearchResult(
                query, Collections.emptyList(), 0);

        when(braveSearchService.searchAndExtractContent(query)).thenReturn(searchResult);

        // Act
        ActionResult result = deepSearchAction.execute(params);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals("Aucun résultat trouvé pour la recherche.", result.getMessage());
        assertTrue(((List<String>)result.getData()).isEmpty());
    }
}
