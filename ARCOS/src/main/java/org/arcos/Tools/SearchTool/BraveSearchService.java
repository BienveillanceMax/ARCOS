package org.arcos.Tools.SearchTool;


import org.arcos.Exceptions.SearchException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

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

@Service
public class BraveSearchService {

    private static final Logger logger = LoggerFactory.getLogger(BraveSearchService.class);
    private static final String BRAVE_API_BASE_URL = "https://api.search.brave.com/res/v1/web/search";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public BraveSearchService() {
        this.apiKey = System.getenv("BRAVE_SEARCH_API_KEY");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty();
    }

    /**
     * Effectue une recherche web avec des paramètres par défaut
     */
    public SearchResult search(String query) throws SearchException {
        return search(query, SearchOptions.defaultOptions());
    }

    public SearchResult searchAndExtractContent(String query) throws SearchException {
        SearchResult searchResult = search(query, SearchOptions.defaultOptions());

        if (searchResult.hasResults()) {
            SearchResultItem topResult = searchResult.getItems().get(0);
            try {
                String content = fetchAndExtractContent(topResult.getUrl());
                topResult.setExtractedContent(content);
            } catch (IOException | InterruptedException e) {
                logger.error("Failed to fetch and extract content for url: {}", topResult.getUrl(), e);
            }
        }

        return searchResult;
    }

    private String fetchAndExtractContent(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return Jsoup.parse(response.body()).text();
    }


    /**
     * Effectue une recherche web avec des options personnalisées
     */
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

            logger.debug("Recherche Brave: {} avec options: {}", query, options);

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
        url.append("&freshness=").append(options.getFreshness().getValue());

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
                        result.title,
                        result.url,
                        result.description,
                        result.publishedDate
                ));
            }
        }

        return new SearchResult(
                query,
                items,
                apiResponse.web != null ? apiResponse.web.totalCount : 0
        );
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
        private String extractedContent;

        public SearchResultItem(String title, String url, String description, String publishedDate) {
            this.title = title;
            this.url = url;
            this.description = description;
            this.publishedDate = publishedDate;
        }

        public String getTitle() { return title; }
        public String getUrl() { return url; }
        public String getDescription() { return description; }
        public Optional<String> getPublishedDate() { return Optional.ofNullable(publishedDate); }
        public Optional<String> getExtractedContent() { return Optional.ofNullable(extractedContent); }

        public void setExtractedContent(String extractedContent) {
            this.extractedContent = extractedContent;
        }

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
    }
}
