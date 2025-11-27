package Tools.Actions;

import Exceptions.SearchException;
import Memory.Actions.Entities.ActionResult;
import Tools.SearchTool.BraveSearchService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class SearchActions
{

    private final BraveSearchService searchService;

    @Autowired
    public SearchActions(BraveSearchService searchService) {
        this.searchService = searchService;
    }

    @RateLimiter(name = "mistral_free")
    @Tool(name = "Chercher_sur_Internet", description = "Recherche des informations sur le web. [Instruction : ne précise tes sources que si cela a un vrai intérêt.]" +
            "Ne peut pas accéder au contenu complet des pages, seulement aux métadonnées des résultats.")
    public ActionResult searchTheWeb(Map<String, Object> params) {

        long startTime = System.currentTimeMillis();
        String query = (String) params.get("query");
        BraveSearchService.SearchResult result;

        try {
            // On limite à 5 résultats pour être concis
            BraveSearchService.SearchOptions options = BraveSearchService.SearchOptions.defaultOptions()
                    .withCount(5);
            result = searchService.search(query, options);
        } catch (SearchException e) {
            return ActionResult.failure("Erreur de Recherche: " + e.getMessage(), e)
                    .withExecutionTime(System.currentTimeMillis() - startTime);
        }

        List<String> processedResults = new ArrayList<>();
        processedResults.add("### Résultats de recherche pour \"" + result.getQuery() + "\"\n");

        int index = 1;
        for (BraveSearchService.SearchResultItem item : result.getItems()) {
            StringBuilder builder = new StringBuilder();
            builder.append(index++).append(". **").append(item.getTitle()).append("**");

            item.getPublishedDate().ifPresent(date ->
                builder.append(" (").append(date).append(")")
            );

            builder.append("\n   ").append(item.getDescription());
            builder.append("\n   Source: ").append(item.getUrl());
            builder.append("\n");

            processedResults.add(builder.toString());
        }

        if (processedResults.size() <= 1) {
            processedResults.add("Aucun résultat pertinent trouvé.");
        }

        return ActionResult.success(processedResults, "Recherche effectuée avec succès")
                .addMetadata("query", query)
                .withExecutionTime(System.currentTimeMillis() - startTime);
    }



}
