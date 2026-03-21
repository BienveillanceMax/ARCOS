package org.arcos.UnitTests.Tools.GdeltTool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.arcos.Tools.GdeltTool.GdeltDocClient;
import org.arcos.Tools.GdeltTool.GdeltDocClient.ArtlistResponse;
import org.arcos.Tools.GdeltTool.GdeltDocClient.TimelineResponse;
import org.arcos.Tools.GdeltTool.GdeltProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GdeltDocClientTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private GdeltDocClient client;
    private GdeltProperties properties;

    @BeforeEach
    void setUp() {
        properties = new GdeltProperties();
        properties.setRateLimitMs(0); // No rate limiting in tests
        properties.setBaseUrl("https://api.gdeltproject.org/api/v2/doc/doc");
        properties.setTimeoutSeconds(10);
        client = new GdeltDocClient(httpClient, new ObjectMapper(), properties);
    }

    @Test
    void fetchArticles_withValidResponse_shouldParseCorrectly() throws Exception {
        // Given
        String json = """
                {
                  "articles": [
                    {
                      "url": "https://www.lemonde.fr/article1",
                      "title": "Article Test",
                      "seendate": "20260320T120000Z",
                      "domain": "lemonde.fr",
                      "language": "French",
                      "sourcecountry": "France"
                    },
                    {
                      "url": "https://www.lefigaro.fr/article2",
                      "title": "Deuxieme Article",
                      "seendate": "20260320T100000Z",
                      "domain": "lefigaro.fr",
                      "language": "French",
                      "sourcecountry": "France"
                    }
                  ]
                }
                """;
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(httpResponse);

        // When
        ArtlistResponse response = client.fetchArticles("climate sourcelang:french", "24hours", 10, "HybridRel");

        // Then
        assertThat(response.articles()).hasSize(2);
        assertThat(response.articles().getFirst().title()).isEqualTo("Article Test");
        assertThat(response.articles().getFirst().domain()).isEqualTo("lemonde.fr");
        assertThat(response.articles().get(1).sourcecountry()).isEqualTo("France");
    }

    @Test
    void fetchArticles_withExtraFields_shouldIgnoreThem() throws Exception {
        // Given — response contains url_mobile and socialimage that we don't map
        String json = """
                {
                  "articles": [
                    {
                      "url": "https://example.com/a",
                      "url_mobile": "",
                      "title": "Test",
                      "seendate": "20260320T120000Z",
                      "socialimage": "https://img.example.com/photo.jpg",
                      "domain": "example.com",
                      "language": "English",
                      "sourcecountry": "United States"
                    }
                  ]
                }
                """;
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(httpResponse);

        // When
        ArtlistResponse response = client.fetchArticles("test", "24hours", 5, "HybridRel");

        // Then
        assertThat(response.articles()).hasSize(1);
        assertThat(response.articles().getFirst().title()).isEqualTo("Test");
    }

    @Test
    void fetchTimeline_withTimelinetoneResponse_shouldParseCorrectly() throws Exception {
        // Given
        String json = """
                {
                  "timeline": [
                    {
                      "series": "Tone",
                      "data": [
                        {"date": "20260318T000000Z", "value": -2.34},
                        {"date": "20260319T000000Z", "value": -1.87},
                        {"date": "20260320T000000Z", "value": 0.52}
                      ]
                    }
                  ]
                }
                """;
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(httpResponse);

        // When
        TimelineResponse response = client.fetchTimeline("ukraine", "timelinetone", "1week");

        // Then
        assertThat(response.timeline()).hasSize(1);
        assertThat(response.timeline().getFirst().series()).isEqualTo("Tone");
        assertThat(response.timeline().getFirst().data()).hasSize(3);
        assertThat(response.timeline().getFirst().data().getFirst().value()).isEqualTo(-2.34);
    }

    @Test
    void fetchTimeline_withSourcecountryResponse_shouldParseMultipleSeries() throws Exception {
        // Given
        String json = """
                {
                  "query_details": {"title": "test", "date_resolution": "day"},
                  "timeline": [
                    {
                      "series": "France Volume Intensity",
                      "data": [{"date": "20260320T000000Z", "value": 5.12}]
                    },
                    {
                      "series": "United States Volume Intensity",
                      "data": [{"date": "20260320T000000Z", "value": 12.34}]
                    }
                  ]
                }
                """;
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(httpResponse);

        // When
        TimelineResponse response = client.fetchTimeline("AI", "timelinesourcecountry", "1week");

        // Then
        assertThat(response.timeline()).hasSize(2);
        assertThat(response.timeline().getFirst().series()).isEqualTo("France Volume Intensity");
    }

    @Test
    void fetchArticles_withEmptyBody_shouldReturnEmptyList() throws Exception {
        // Given — GDELT returns empty body for 0 results
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("");
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(httpResponse);

        // When
        ArtlistResponse response = client.fetchArticles("nonexistent_query_xyz", "24hours", 5, "HybridRel");

        // Then
        assertThat(response.articles()).isEmpty();
    }

    @Test
    void fetchTimeline_withEmptyBody_shouldReturnEmptyTimeline() throws Exception {
        // Given
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("");
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(httpResponse);

        // When
        TimelineResponse response = client.fetchTimeline("nonexistent", "timelinetone", "1week");

        // Then
        assertThat(response.timeline()).isEmpty();
    }

    @Test
    void fetchArticles_withHttpError_shouldReturnEmptyList() throws Exception {
        // Given
        when(httpResponse.statusCode()).thenReturn(429);
        when(httpResponse.body()).thenReturn("Rate limit exceeded");
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(httpResponse);

        // When
        ArtlistResponse response = client.fetchArticles("test", "24hours", 5, "HybridRel");

        // Then
        assertThat(response.articles()).isEmpty();
    }

    @Test
    void fetchArticles_withNetworkError_shouldReturnEmptyList() throws Exception {
        // Given
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenThrow(new java.io.IOException("Connection refused"));

        // When
        ArtlistResponse response = client.fetchArticles("test", "24hours", 5, "HybridRel");

        // Then
        assertThat(response.articles()).isEmpty();
    }
}
