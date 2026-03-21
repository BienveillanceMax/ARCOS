package org.arcos.Tools.GdeltTool;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "arcos.gdelt.enabled", havingValue = "true", matchIfMissing = true)
public class GdeltDocClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final GdeltProperties properties;
    private long lastCallTime = 0;

    @Autowired
    public GdeltDocClient(HttpClient gdeltHttpClient, GdeltProperties properties) {
        this.httpClient = gdeltHttpClient;
        this.objectMapper = new ObjectMapper();
        this.properties = properties;
    }

    // Constructor for testing
    public GdeltDocClient(HttpClient httpClient, ObjectMapper objectMapper, GdeltProperties properties) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public ArtlistResponse fetchArticles(String query, String timespan, int maxRecords, String sort) {
        respectRateLimit();
        String url = buildUrl(query, "artlist", timespan, maxRecords, sort);
        String body = executeRequest(url);
        lastCallTime = System.currentTimeMillis(); // track response time, not request time
        if (body == null || body.isBlank()) {
            return new ArtlistResponse(List.of());
        }
        try {
            return objectMapper.readValue(body, ArtlistResponse.class);
        } catch (Exception e) {
            log.warn("Failed to parse artlist response: {}", e.getMessage());
            return new ArtlistResponse(List.of());
        }
    }

    public TimelineResponse fetchTimeline(String query, String mode, String timespan) {
        respectRateLimit();
        String url = buildUrl(query, mode, timespan, 0, null);
        String body = executeRequest(url);
        lastCallTime = System.currentTimeMillis(); // track response time, not request time
        if (body == null || body.isBlank()) {
            return new TimelineResponse(List.of());
        }
        try {
            return objectMapper.readValue(body, TimelineResponse.class);
        } catch (Exception e) {
            log.warn("Failed to parse timeline response for mode {}: {}", mode, e.getMessage());
            return new TimelineResponse(List.of());
        }
    }

    private String buildUrl(String query, String mode, String timespan, int maxRecords, String sort) {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(properties.getBaseUrl());
        sb.append("?query=").append(encodedQuery);
        sb.append("&mode=").append(mode);
        sb.append("&format=json");
        sb.append("&timespan=").append(timespan);
        if (maxRecords > 0) {
            sb.append("&maxrecords=").append(maxRecords);
        }
        if (sort != null && !sort.isBlank()) {
            sb.append("&sort=").append(sort);
        }
        return sb.toString();
    }

    private String executeRequest(String url) {
        try {
            log.debug("GDELT API call: {}", url);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "ARCOS/1.0")
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("GDELT API returned HTTP {}: {}", response.statusCode(),
                        response.body().substring(0, Math.min(200, response.body().length())));
                return null;
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("GDELT API call interrupted");
            return null;
        } catch (Exception e) {
            log.warn("GDELT API call failed: {}", e.getMessage());
            return null;
        }
    }

    private void respectRateLimit() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastCallTime;
        long rateLimitMs = properties.getRateLimitMs();
        if (lastCallTime > 0 && elapsed < rateLimitMs) {
            try {
                long sleepTime = rateLimitMs - elapsed;
                log.debug("Rate limiting: sleeping {}ms", sleepTime);
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // --- Response DTOs ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GdeltArticle(
            String url,
            String title,
            String seendate,
            String domain,
            String language,
            String sourcecountry
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ArtlistResponse(List<GdeltArticle> articles) {
        public ArtlistResponse {
            if (articles == null) articles = List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TimelineDataPoint(String date, double value) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TimelineSeries(String series, List<TimelineDataPoint> data) {
        public TimelineSeries {
            if (data == null) data = List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TimelineResponse(List<TimelineSeries> timeline) {
        public TimelineResponse {
            if (timeline == null) timeline = List.of();
        }
    }
}
