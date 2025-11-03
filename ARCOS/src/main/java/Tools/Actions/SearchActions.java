package Tools.Actions;

import Exceptions.SearchException;
import Memory.Actions.Entities.ActionResult;
import Tools.SearchTool.BraveSearchService;
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

    @Tool(name = "Chercher_sur_Internet", description = "Recherche des informations sur le web. [Instruction : ne précise tes sources que si cela a un vrai intérêt.]" +
            "Ne peut pas accéder au contenu complet des pages, seulement aux métadonnées des résultats.")
    public ActionResult searchTheWeb(Map<String, Object> params) {

        long startTime = System.currentTimeMillis();
        String query = (String) params.get("query");
        BraveSearchService.SearchResult result;

        try {
            result = searchService.search(query);
        } catch (SearchException e) {
            return ActionResult.failure("Erreur de Recherche: " + e.getMessage(), e)
                    .withExecutionTime(System.currentTimeMillis() - startTime);

        }

        List<String> processedResults = new ArrayList<>();
        processedResults.add("[Instruction : ne précise tes sources que si cela a un vrai intérêt.] Recherche : " + result.getQuery() + '\n');
        processedResults.add("Nombre de résultats : " + (long) result.getItems().size() + '\n');

        for (BraveSearchService.SearchResultItem item : result.getItems()) {
            StringBuilder builder = new StringBuilder();
            builder.append('\n');
            builder.append("Debut de l'objet recherche");
            builder.append("Titre : ");
            builder.append(item.getTitle());
            builder.append('\n' + '\t');
            builder.append("URL : ");
            builder.append(item.getUrl());
            builder.append('\n' + '\t');
            builder.append("Date de publication : ");
            builder.append(item.getPublishedDate());
            builder.append('\n' + '\t');
            builder.append("Description : ");
            builder.append(item.getDescription());
            processedResults.add(builder.toString());
            builder.append('\n' + '\t');
        }
        return ActionResult.success(processedResults, "Recherche effectuée avec succès")
                .addMetadata("query", query)
                .withExecutionTime(System.currentTimeMillis() - startTime);
    }



}
