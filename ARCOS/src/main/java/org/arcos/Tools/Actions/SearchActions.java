package org.arcos.Tools.Actions;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.arcos.Exceptions.SearchException;
import org.arcos.IO.OuputHandling.StateHandler.CentralFeedBackHandler;
import org.arcos.IO.OuputHandling.StateHandler.FeedBackEvent;
import org.arcos.IO.OuputHandling.StateHandler.UXEventType;
import org.arcos.Tools.SearchTool.BraveSearchService;
import org.arcos.Tools.SearchTool.BraveSearchService.Freshness;
import org.arcos.Tools.SearchTool.DeepSearchService;
import org.arcos.Tools.SearchTool.SearchResultFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
public class SearchActions
{
    private final BraveSearchService searchService;
    private final DeepSearchService deepSearchService;
    private final SearchResultFormatter formatter;
    private final CentralFeedBackHandler centralFeedBackHandler;
    private final int braveResultCount;
    private final String country;
    private final String language;
    private final boolean deepEnabled;
    private final int totalChars;

    @Autowired
    public SearchActions(BraveSearchService searchService,
                         DeepSearchService deepSearchService,
                         SearchResultFormatter formatter,
                         CentralFeedBackHandler centralFeedBackHandler,
                         @Value("${arcos.search.brave-result-count:5}") int braveResultCount,
                         @Value("${arcos.search.country:FR}") String country,
                         @Value("${arcos.search.language:fr}") String language,
                         @Value("${arcos.search.deep.enabled:true}") boolean deepEnabled,
                         @Value("${arcos.search.deep.total-chars:6000}") int totalChars) {
        this.searchService = searchService;
        this.deepSearchService = deepSearchService;
        this.formatter = formatter;
        this.centralFeedBackHandler = centralFeedBackHandler;
        this.braveResultCount = braveResultCount;
        this.country = country;
        this.language = language;
        this.deepEnabled = deepEnabled;
        this.totalChars = totalChars;
    }

    @Tool(name = "Chercher_sur_Internet", description =
            "Recherche approfondie sur le web : interroge un moteur de recherche puis lit automatiquement "
          + "le contenu des meilleures pages. Retourne extraits pertinents et sources. "
          + "Utiliser mode='rapide' pour ne récupérer que les extraits (plus rapide). "
          + "[Instruction : ne précise tes sources que si cela a un vrai intérêt.]")
    public ActionResult searchTheWeb(
            @ToolParam(description = "Termes de recherche (mots-clés)") String query,
            @ToolParam(required = false, description =
                    "Fraîcheur des résultats : 'jour', 'semaine', 'mois' ou 'annee'. Omettre pour toutes dates.")
            String fraicheur,
            @ToolParam(required = false, description =
                    "Mode : 'approfondi' (lit le contenu des meilleures pages, défaut) ou 'rapide' (extraits seulement).")
            String mode,
            @ToolParam(required = false, description =
                    "Numéro de page de résultats (1 par défaut). Utiliser 2, 3... pour voir plus de résultats.")
            Integer page) {
        if (!searchService.isAvailable()) {
            log.warn("Recherche web demandée mais BRAVE_SEARCH_API_KEY absent.");
            return ActionResult.failure("Recherche web non disponible : BRAVE_SEARCH_API_KEY non configurée.", null)
                    .withExecutionTime(0);
        }

        boolean deep = deepEnabled && !"rapide".equalsIgnoreCase(mode == null ? "" : mode.trim());
        String effectiveMode = deep ? "approfondi" : "rapide";
        log.info("Recherche web ({}) : {}", effectiveMode, query);

        long startTime = System.currentTimeMillis();
        List<String> warnings = new ArrayList<>();

        centralFeedBackHandler.handleFeedBack(new FeedBackEvent(UXEventType.LONGTASK_START));
        try {
            BraveSearchService.SearchOptions options = BraveSearchService.SearchOptions.defaultOptions()
                    .withCount(braveResultCount)
                    .withFreshness(parseFraicheur(fraicheur, warnings))
                    .withOffset(resolveOffset(page));
            if (!country.isBlank()) {
                options.withCountry(country);
            }
            if (!language.isBlank()) {
                options.withLanguage(language);
            }

            BraveSearchService.SearchResult result = searchService.search(query, options);
            List<BraveSearchService.SearchResultItem> items = result.getItems();

            List<DeepSearchService.DeepPage> deepPages = List.of();
            if (deep && !items.isEmpty()) {
                DeepSearchService.DeepSearchOutcome outcome = deepSearchService.enrich(query, items);
                deepPages = outcome.pages();
                warnings.addAll(outcome.warnings());
            }

            List<String> data = formatter.format(query, items, deepPages, totalChars);

            return ActionResult.success(data, "Recherche effectuée avec succès")
                    .addMetadata("query", query)
                    .addMetadata("mode", effectiveMode)
                    .addMetadata("pages_lues", deepPages.size())
                    .addWarnings(warnings)
                    .withExecutionTime(System.currentTimeMillis() - startTime);

        } catch (SearchException e) {
            log.error("Erreur de recherche Brave : {}", e.getMessage());
            return ActionResult.failure("Erreur de recherche : " + e.getMessage(), e)
                    .withExecutionTime(System.currentTimeMillis() - startTime);
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker braveSearch ouvert : {}", e.getMessage());
            return ActionResult.failure("Service de recherche temporairement indisponible.", null)
                    .withExecutionTime(System.currentTimeMillis() - startTime);
        } finally {
            centralFeedBackHandler.handleFeedBack(new FeedBackEvent(UXEventType.LONGTASK_END));
        }
    }

    /** Mapping tolérant des valeurs françaises de fraîcheur ; valeur inconnue → toutes dates + warning. */
    private Freshness parseFraicheur(String fraicheur, List<String> warnings) {
        if (fraicheur == null || fraicheur.isBlank()) {
            return Freshness.ALL;
        }
        String normalized = fraicheur.toLowerCase(Locale.FRENCH);
        if (normalized.contains("jour") || normalized.contains("aujourd")) {
            return Freshness.PAST_DAY;
        }
        if (normalized.contains("semaine")) {
            return Freshness.PAST_WEEK;
        }
        if (normalized.contains("mois")) {
            return Freshness.PAST_MONTH;
        }
        if (normalized.contains("an")) {
            return Freshness.PAST_YEAR;
        }
        warnings.add("Fraîcheur inconnue : " + fraicheur);
        return Freshness.ALL;
    }

    /** L'offset Brave compte des pages de `count` résultats, borné à 9 par l'API. */
    private static int resolveOffset(Integer page) {
        if (page == null || page < 1) {
            return 0;
        }
        return Math.min(page - 1, 9);
    }
}
