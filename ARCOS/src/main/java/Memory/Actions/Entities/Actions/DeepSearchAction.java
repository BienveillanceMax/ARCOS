package Memory.Actions.Entities.Actions;

import Exceptions.SearchException;
import Memory.Actions.Entities.ActionResult;
import Memory.Actions.Entities.Parameter;
import Tools.SearchTool.BraveSearchService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DeepSearchAction extends Action {

    BraveSearchService searchService;

    private static List<Parameter> createParameters() {
        return Collections.singletonList(
                new Parameter(
                        "query",
                        String.class,
                        true,
                        "Requête de recherche à effectuer sur le web",
                        null
                )
        );
    }

    public DeepSearchAction(BraveSearchService searchService) {
        super("Recherche approfondie sur internet",
                "Effectue une recherche web et extrait le contenu textuel principal de la première page de résultat.",
                createParameters());

        this.searchService = searchService;
    }

    @Override
    public ActionResult execute(Map<String, Object> params) {
        long startTime = System.currentTimeMillis();
        String query = (String) params.get("query");
        BraveSearchService.SearchResult result;

        try {
            result = searchService.searchAndExtractContent(query);
        } catch (SearchException e) {
            return ActionResult.failure("Erreur de Recherche: " + e.getMessage(), e)
                    .withExecutionTime(System.currentTimeMillis() - startTime);
        }

        if (!result.hasResults()) {
            return ActionResult.success(new ArrayList<>(), "Aucun résultat trouvé pour la recherche.")
                    .withExecutionTime(System.currentTimeMillis() - startTime);
        }

        List<String> processedResults = new ArrayList<>();
        processedResults.add("[Instruction : ne précise tes sources que si cela a un vrai intérêt.] Recherche : " + result.getQuery() + '\n');
        processedResults.add("Nombre de résultats : " + result.getItems().size() + '\n');

        // Traiter le premier résultat avec le contenu extrait
        BraveSearchService.SearchResultItem topItem = result.getItems().get(0);
        StringBuilder topBuilder = new StringBuilder();
        topBuilder.append('\n');
        topBuilder.append("Debut de l'objet recherche (Top Résultat)");
        topBuilder.append("Titre : ");
        topBuilder.append(topItem.getTitle());
        topBuilder.append('\n' + '\t');
        topBuilder.append("URL : ");
        topBuilder.append(topItem.getUrl());
        topBuilder.append('\n' + '\t');
        topBuilder.append("Date de publication : ");
        topBuilder.append(topItem.getPublishedDate().orElse("Non disponible"));
        topBuilder.append('\n' + '\t');
        topBuilder.append("Description : ");
        topBuilder.append(topItem.getDescription());
        topBuilder.append('\n' + '\t');
        topBuilder.append("Contenu extrait : ");
        topBuilder.append(topItem.getExtractedContent().orElse("N/A"));
        processedResults.add(topBuilder.toString());

        // Traiter les autres résultats
        for (int i = 1; i < result.getItems().size(); i++) {
            BraveSearchService.SearchResultItem item = result.getItems().get(i);
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
            builder.append(item.getPublishedDate().orElse("Non disponible"));
            builder.append('\n' + '\t');
            builder.append("Description : ");
            builder.append(item.getDescription());
            processedResults.add(builder.toString());
        }

        return ActionResult.success(processedResults, "Recherche approfondie effectuée avec succès")
                .addMetadata("query", query)
                .withExecutionTime(System.currentTimeMillis() - startTime);
    }
}
