package org.arcos.Tools.SearchTool;


import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.arcos.Exceptions.SearchException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.parser.Parser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service de recherche web utilisant l'API Brave Search
 * Conçu pour être intégré dans un assistant IA
 */

@Slf4j
@Service
public class BraveSearchService {

    private static final String BRAVE_API_BASE_URL = "https://api.search.brave.com/res/v1/web/search";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    @Autowired
    public BraveSearchService(@Value("${BRAVE_SEARCH_API_KEY:}") String apiKey) {
        this(HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build(),
                new ObjectMapper(),
                apiKey);
    }

    public BraveSearchService(HttpClient httpClient, ObjectMapper objectMapper, String apiKey) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("BRAVE_SEARCH_API_KEY absent — recherche web désactivée.");
        }
    }

    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Effectue une recherche web avec des options personnalisées
     */
    @CircuitBreaker(name = "braveSearch")
    public SearchResult search(String query, SearchOptions options) throws SearchException {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = buildSearchUrl(encodedQuery, options);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-Subscription-Token", apiKey)
                    .header("Accept", "application/json")
                    .header("User-Agent", "AI-Assistant/1.0")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            log.debug("Recherche Brave: {} avec options: {}", query, options);

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new SearchException("Erreur API Brave: " + response.statusCode() +
                        " - " + response.body());
            }

            BraveApiResponse apiResponse = objectMapper.readValue(response.body(),
                    BraveApiResponse.class);

            return convertToSearchResult(apiResponse, query);

        } catch (IOException | InterruptedException e) {
            throw new SearchException("Erreur lors de la recherche: " + e.getMessage(), e);
        }
    }

    /**
     * Construit l'URL de recherche avec les paramètres
     */
    private String buildSearchUrl(String encodedQuery, SearchOptions options) {
        StringBuilder url = new StringBuilder(BRAVE_API_BASE_URL);
        url.append("?q=").append(encodedQuery);
        url.append("&count=").append(options.getCount());
        url.append("&offset=").append(options.getOffset());
        url.append("&safesearch=").append(options.getSafeSearch().getValue());
        url.append("&extra_snippets=true");      // ignoré par les plans Brave qui ne le supportent pas
        url.append("&text_decorations=false");   // pas de marqueurs de surlignage dans les descriptions

        if (options.getFreshness() != Freshness.ALL) {
            url.append("&freshness=").append(options.getFreshness().getValue());
        }

        if (options.getCountry() != null) {
            url.append("&country=").append(options.getCountry());
        }

        if (options.getLanguage() != null) {
            url.append("&search_lang=").append(options.getLanguage());
        }

        return url.toString();
    }

    /**
     * Convertit la réponse de l'API Brave en résultat utilisable
     */
    private SearchResult convertToSearchResult(BraveApiResponse apiResponse, String query) {
        List<SearchResultItem> items = new ArrayList<>();

        if (apiResponse.web != null && apiResponse.web.results != null) {
            for (BraveWebResult result : apiResponse.web.results) {
                items.add(new SearchResultItem(
                        unescapeHtml(result.title),
                        result.url,
                        unescapeHtml(result.description),
                        effectiveDate(result.publishedDate, result.pageAge),
                        result.extraSnippets == null ? List.of()
                                : result.extraSnippets.stream().map(BraveSearchService::unescapeHtml).toList()
                ));
            }
        }

        return new SearchResult(
                query,
                items,
                apiResponse.web != null ? apiResponse.web.totalCount : 0
        );
    }

    /** Brave renvoie titres/descriptions HTML-échappés (&#x27; etc.) — inutilisable tel quel en vocal. */
    private static String unescapeHtml(String value) {
        return value == null ? null : Parser.unescapeEntities(value, false);
    }

    /** Date affichable : `published` si présent, sinon `page_age` réduit à sa partie date ISO. */
    private static String effectiveDate(String published, String pageAge) {
        if (published != null && !published.isBlank()) {
            return published;
        }
        if (pageAge == null || pageAge.isBlank()) {
            return null;
        }
        return pageAge.length() >= 10 ? pageAge.substring(0, 10) : pageAge;
    }

    // Classes de données pour les résultats

    /**
     * Résultat de recherche principal
     */
    public static class SearchResult {
        private final String query;
        private final List<SearchResultItem> items;
        private final long totalResults;

        public SearchResult(String query, List<SearchResultItem> items, long totalResults) {
            this.query = query;
            this.items = new ArrayList<>(items);
            this.totalResults = totalResults;
        }

        public String getQuery() { return query; }
        public List<SearchResultItem> getItems() { return new ArrayList<>(items); }
        public long getTotalResults() { return totalResults; }
        public boolean hasResults() { return !items.isEmpty(); }

        @Override
        public String toString() {
            return String.format("SearchResult{query='%s', items=%d, total=%d}",
                    query, items.size(), totalResults);
        }
    }

    /**
     * Item individuel du résultat de recherche
     */
    public static class SearchResultItem {
        private final String title;
        private final String url;
        private final String description;
        private final String publishedDate;
        private final List<String> extraSnippets;

        public SearchResultItem(String title, String url, String description, String publishedDate) {
            this(title, url, description, publishedDate, List.of());
        }

        public SearchResultItem(String title, String url, String description, String publishedDate,
                                List<String> extraSnippets) {
            this.title = title;
            this.url = url;
            this.description = description;
            this.publishedDate = publishedDate;
            this.extraSnippets = List.copyOf(extraSnippets);
        }

        public String getTitle() { return title; }
        public String getUrl() { return url; }
        public String getDescription() { return description; }
        public Optional<String> getPublishedDate() { return Optional.ofNullable(publishedDate); }
        public List<String> getExtraSnippets() { return extraSnippets; }

        @Override
        public String toString() {
            return String.format("SearchResultItem{title='%s', url='%s'}", title, url);
        }
    }

    /**
     * Options de recherche
     */
    public static class SearchOptions {
        private int count = 10;
        private int offset = 0;
        private SafeSearch safeSearch = SafeSearch.MODERATE;
        private Freshness freshness = Freshness.ALL;
        private String country;
        private String language;

        public static SearchOptions defaultOptions() {
            return new SearchOptions();
        }

        public SearchOptions withCount(int count) {
            this.count = Math.max(1, Math.min(count, 20)); // API Brave limite à 20
            return this;
        }

        public SearchOptions withOffset(int offset) {
            this.offset = Math.max(0, offset);
            return this;
        }

        public SearchOptions withSafeSearch(SafeSearch safeSearch) {
            this.safeSearch = safeSearch;
            return this;
        }

        public SearchOptions withFreshness(Freshness freshness) {
            this.freshness = freshness;
            return this;
        }

        public SearchOptions withCountry(String country) {
            this.country = country;
            return this;
        }

        public SearchOptions withLanguage(String language) {
            this.language = language;
            return this;
        }

        // Getters
        public int getCount() { return count; }
        public int getOffset() { return offset; }
        public SafeSearch getSafeSearch() { return safeSearch; }
        public Freshness getFreshness() { return freshness; }
        public String getCountry() { return country; }
        public String getLanguage() { return language; }

        @Override
        public String toString() {
            return String.format("SearchOptions{count=%d, offset=%d, safeSearch=%s, freshness=%s}",
                    count, offset, safeSearch, freshness);
        }
    }

    /**
     * Niveaux de filtrage de contenu
     */
    public enum SafeSearch {
        OFF("off"),
        MODERATE("moderate"),
        STRICT("strict");

        private final String value;

        SafeSearch(String value) {
            this.value = value;
        }

        public String getValue() { return value; }
    }

    /**
     * Options de fraîcheur du contenu
     */
    public enum Freshness {
        ALL(""),
        PAST_DAY("pd"),
        PAST_WEEK("pw"),
        PAST_MONTH("pm"),
        PAST_YEAR("py");

        private final String value;

        Freshness(String value) {
            this.value = value;
        }

        public String getValue() { return value; }
    }

    // Classes pour le mapping JSON de l'API Brave

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class BraveApiResponse {
        @JsonProperty("web")
        public BraveWebResponse web;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class BraveWebResponse {
        @JsonProperty("results")
        public List<BraveWebResult> results;

        @JsonProperty("totalCount")
        public long totalCount;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class BraveWebResult {
        @JsonProperty("title")
        public String title;

        @JsonProperty("url")
        public String url;

        @JsonProperty("description")
        public String description;

        @JsonProperty("published")
        public String publishedDate;

        @JsonProperty("page_age")
        public String pageAge;

        @JsonProperty("extra_snippets")
        public List<String> extraSnippets;
    }
}
