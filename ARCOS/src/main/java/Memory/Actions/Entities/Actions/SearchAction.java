package Memory.Actions.Entities.Actions;

import Exceptions.SearchException;
import Memory.Actions.Entities.ActionResult;
import Memory.Actions.Entities.Parameter;
import Tools.SearchTool.BraveSearchService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class SearchAction extends Action
{
    BraveSearchService searchService;


    public SearchAction(BraveSearchService searchService) {
        super("Rechercher sur internet",
                "Recherche des informations sur le web. Ne peut pas accéder au contenu complet des pages, seulement aux métadonnées des résultats de recherche.",
                createParameters());

        this.searchService = searchService;
    }

    @Override
    public ActionResult execute(Map<String, Object> params) {

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


    private static List<Parameter> createParameters() {
        return Arrays.asList(
                // Paramètre obligatoire : la requête de recherche
                new Parameter(
                        "query",
                        String.class,
                        true,
                        "Requête de recherche à effectuer sur le web",
                        null
                ),

                // Paramètres optionnels avec valeurs par défaut
                new Parameter(
                        "count",
                        Integer.class,
                        false,
                        "Nombre de résultats à retourner (1-20)",
                        10
                ),

                new Parameter(
                        "offset",
                        Integer.class,
                        false,
                        "Nombre de résultats à ignorer (pagination)",
                        0
                ),

                new Parameter(
                        "safeSearch",
                        String.class,
                        false,
                        "Niveau de filtrage du contenu : 'off', 'moderate', 'strict'",
                        "moderate"
                ),

                new Parameter(
                        "freshness",
                        String.class,
                        false,
                        "Fraîcheur du contenu : 'all', 'past_day', 'past_week', 'past_month', 'past_year'",
                        "all"
                ),

                new Parameter(
                        "country",
                        String.class,
                        false,
                        "Code pays pour localiser les résultats (ex: 'FR', 'US', 'GB')",
                        null
                ),

                new Parameter(
                        "language",
                        String.class,
                        false,
                        "Code langue pour les résultats (ex: 'fr', 'en', 'es')",
                        null
                )
        );
    }
}
