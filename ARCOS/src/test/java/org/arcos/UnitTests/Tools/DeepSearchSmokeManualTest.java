package org.arcos.UnitTests.Tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.arcos.IO.OuputHandling.StateHandler.CentralFeedBackHandler;
import org.arcos.Tools.Actions.ActionResult;
import org.arcos.Tools.Actions.SearchActions;
import org.arcos.Tools.SearchTool.BraveSearchService;
import org.arcos.Tools.SearchTool.DeepSearchService;
import org.arcos.Tools.SearchTool.SearchResultFormatter;
import org.arcos.Tools.WebCommon.ContentExtractor;
import org.arcos.Tools.WebCommon.PageFetcher;
import org.arcos.Tools.WebCommon.QueryRelevanceRanker;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Smoke manuel du deep search contre le vrai Brave + le vrai web.
 * Lancé à la main : BRAVE_SEARCH_API_KEY requis (jamais en CI).
 */
@Tag("smoke-manual")
class DeepSearchSmokeManualTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "BRAVE_SEARCH_API_KEY", matches = ".+")
    void deepSearch_AgainstRealWeb_ShouldReturnPageContent() {
        // Given — chaîne réelle complète
        BraveSearchService brave = new BraveSearchService(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                new ObjectMapper(),
                System.getenv("BRAVE_SEARCH_API_KEY"));
        PageFetcher fetcher = new PageFetcher(new org.arcos.Tools.WebCommon.SsrfGuard());
        ContentExtractor extractor = new ContentExtractor();
        DeepSearchService deep = new DeepSearchService(fetcher, extractor,
                new QueryRelevanceRanker(), 2, 3, 1200, 4);
        SearchActions actions = new SearchActions(brave, deep, new SearchResultFormatter(),
                mock(CentralFeedBackHandler.class), 5, "FR", "fr", true, 6000);

        // When
        long start = System.currentTimeMillis();
        ActionResult result = actions.searchTheWeb("actualité spatiale lancement fusée", "semaine", null, null);
        long elapsed = System.currentTimeMillis() - start;

        // Then
        System.out.println("=== Durée : " + elapsed + " ms — pages_lues="
                + result.getMetadata().get("pages_lues") + " warnings=" + result.getWarnings());
        ((List<String>) result.getData()).forEach(s -> System.out.println("--- section ---\n" + s));

        assertThat(result.isSuccess()).isTrue();
        List<String> data = (List<String>) result.getData();
        assertThat(data.get(0)).contains("Recherche");
        assertThat(data).noneMatch(s -> s.contains("**"));
        assertThat(elapsed).isLessThan(15000);
    }
}
