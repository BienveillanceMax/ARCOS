package org.arcos.Tools.Actions;

import org.arcos.Exceptions.SearchException;
import org.arcos.Tools.SearchTool.BraveSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SearchActions
{
    private final BraveSearchService searchService;
    private final int braveResultCount;

    @Autowired
    public SearchActions(BraveSearchService searchService,
                         @Value("${arcos.search.brave-result-count:5}") int braveResultCount) {
        this.searchService = searchService;
        this.braveResultCount = braveResultCount;
    }

    @Tool(name = "Chercher_sur_Internet", description = "Recherche des informations sur le web. [Instruction : ne précise tes sources que si cela a un vrai intérêt.]" +
            "Ne peut pas accéder au contenu complet des pages, seulement aux métadonnées des résultats.")
    public ActionResult searchTheWeb(String query) {
        if (!searchService.isAvailable()) {
            log.warn("Recherche web demandée mais BRAVE_SEARCH_API_KEY absent.");
            return ActionResult.failure("Recherche web non disponible : BRAVE_SEARCH_API_KEY non configurée.", null)
                    .withExecutionTime(0);
        }

        log.info("Recherche d'info sur le web");
        log.info("{}", query);

        long startTime = System.currentTimeMillis();
        BraveSearchService.SearchResult result;

        try {
            BraveSearchService.SearchOptions options = BraveSearchService.SearchOptions.defaultOptions()
                    .withCount(braveResultCount);
            result = searchService.search(query, options);
        } catch (SearchException e) {
            log.error(e.getMessage());
            return ActionResult.failure("Erreur de Recherche: " + e.getMessage(), e)
                    .withExecutionTime(System.currentTimeMillis() - startTime);
        }

        List<String> processedResults = new ArrayList<>();
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
