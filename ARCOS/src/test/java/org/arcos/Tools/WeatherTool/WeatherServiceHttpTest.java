package org.arcos.Tools.WeatherTool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.arcos.Tools.WeatherTool.WeatherService.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests pour WeatherService au niveau HTTP (HttpClient mocke).
 *
 * Place dans le meme package que WeatherService pour acceder au constructeur package-private.
 *
 * Valide :
 * - AC4 : geocoding + meteo Open-Meteo => donnees parsees
 * - AC6 : erreurs reseau => propagation propre (IOException)
 */
@ExtendWith(MockitoExtension.class)
class WeatherServiceHttpTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private WeatherService weatherService;

    @BeforeEach
    void setUp() {
        weatherService = new WeatherService(httpClient, new ObjectMapper());
    }

    // ── Geocoding ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Given valid geocoding response, When geocoding city, Then location is parsed correctly")
    void geocode_WithValidResponse_ShouldParseLocation() throws Exception {
        // Given
        String geocodeJson = """
                {
                  "results": [
                    {
                      "name": "Paris",
                      "latitude": 48.8566,
                      "longitude": 2.3522,
                      "country": "France"
                    }
                  ]
                }
                """;
        when(httpResponse.body()).thenReturn(geocodeJson);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(httpResponse);

        // When
        GeoLocation location = weatherService.geocode("Paris");

        // Then
        assertThat(location.name()).isEqualTo("Paris");
        assertThat(location.latitude()).isEqualTo(48.8566);
        assertThat(location.longitude()).isEqualTo(2.3522);
        assertThat(location.country()).isEqualTo("France");
    }

    @Test
    @DisplayName("Given empty geocoding results, When geocoding unknown city, Then IOException with message")
    void geocode_WithNoResults_ShouldThrowIOException() throws Exception {
        // Given
        String emptyJson = "{\"results\": []}";
        when(httpResponse.body()).thenReturn(emptyJson);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(httpResponse);

        // When/Then
        assertThatThrownBy(() -> weatherService.geocode("VilleQuiNExistePas"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Ville introuvable");
    }

    @Test
    @DisplayName("Given null results field in geocoding response, When geocoding, Then IOException")
    void geocode_WithNullResults_ShouldThrowIOException() throws Exception {
        // Given
        String noResultsJson = "{}";
        when(httpResponse.body()).thenReturn(noResultsJson);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(httpResponse);

        // When/Then
        assertThatThrownBy(() -> weatherService.geocode("Unknown"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Ville introuvable");
    }

    @Test
    @DisplayName("Given geocoding without country field, When geocoding, Then country is empty string")
    void geocode_WithoutCountryField_ShouldReturnEmptyCountry() throws Exception {
        // Given
        String json = """
                {
                  "results": [
                    {
                      "name": "TestCity",
                      "latitude": 10.0,
                      "longitude": 20.0
                    }
                  ]
                }
                """;
        when(httpResponse.body()).thenReturn(json);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(httpResponse);

        // When
        GeoLocation location = weatherService.geocode("TestCity");

        // Then
        assertThat(location.country()).isEmpty();
    }

    // ── Weather forecast ────────────────────────────────────────────────────

    @Test
    @DisplayName("Given valid weather response, When fetching forecast, Then data is parsed correctly")
    void getWeather_WithValidResponse_ShouldParseCorrectly() throws Exception {
        // Given
        String weatherJson = """
                {
                  "current": {
                    "temperature_2m": 18.5,
                    "relative_humidity_2m": 65,
                    "wind_speed_10m": 12.3,
                    "weather_code": 2
                  },
                  "daily": {
                    "time": ["2026-03-26", "2026-03-27"],
                    "temperature_2m_min": [10.0, 8.0],
                    "temperature_2m_max": [20.0, 18.0],
                    "weather_code": [0, 61]
                  }
                }
                """;
        when(httpResponse.body()).thenReturn(weatherJson);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(httpResponse);

        // When
        WeatherResult result = weatherService.getWeather(48.8566, 2.3522, 2);

        // Then
        assertThat(result.current().temperature()).isEqualTo(18.5);
        assertThat(result.current().humidity()).isEqualTo(65);
        assertThat(result.current().windSpeed()).isEqualTo(12.3);
        assertThat(result.current().description()).isEqualTo("Partiellement nuageux");
        assertThat(result.forecast()).hasSize(2);
        assertThat(result.forecast().get(0).date()).isEqualTo("2026-03-26");
        assertThat(result.forecast().get(0).description()).isEqualTo("Ciel dégagé");
        assertThat(result.forecast().get(1).description()).isEqualTo("Pluie légère");
    }

    @Test
    @DisplayName("Given unknown weather code, When fetching forecast, Then description contains code number")
    void getWeather_WithUnknownWeatherCode_ShouldReturnDescriptionWithCode() throws Exception {
        // Given
        String weatherJson = """
                {
                  "current": {
                    "temperature_2m": 18.5,
                    "relative_humidity_2m": 65,
                    "wind_speed_10m": 12.3,
                    "weather_code": 999
                  },
                  "daily": {
                    "time": [],
                    "temperature_2m_min": [],
                    "temperature_2m_max": [],
                    "weather_code": []
                  }
                }
                """;
        when(httpResponse.body()).thenReturn(weatherJson);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(httpResponse);

        // When
        WeatherResult result = weatherService.getWeather(0, 0, 1);

        // Then
        assertThat(result.current().description()).contains("999");
    }

    // ── Network failures ────────────────────────────────────────────────────

    @Test
    @DisplayName("Given network failure, When fetching weather, Then IOException propagates")
    void getWeather_WhenNetworkFailure_ShouldPropagateIOException() throws Exception {
        // Given
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenThrow(new IOException("Connection refused"));

        // When/Then
        assertThatThrownBy(() -> weatherService.getWeather(48.8566, 2.3522, 3))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Connection refused");
    }

    @Test
    @DisplayName("Given interrupted request, When geocoding, Then InterruptedException propagates")
    void geocode_WhenInterrupted_ShouldPropagateInterruptedException() throws Exception {
        // Given
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenThrow(new InterruptedException("Thread interrupted"));

        // When/Then
        assertThatThrownBy(() -> weatherService.geocode("Paris"))
                .isInstanceOf(InterruptedException.class);
    }
}
