package org.arcos.Tools.WeatherTool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.arcos.Tools.WeatherTool.WeatherService.*;
import org.junit.jupiter.api.BeforeEach;
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

@ExtendWith(MockitoExtension.class)
class WeatherServiceTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private WeatherService weatherService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        weatherService = new WeatherService(httpClient, objectMapper);
    }

    // ── Geocoding ───────────────────────────────────────────────────────────

    @Test
    void geocode_WithValidCity_ShouldReturnGeoLocation() throws Exception {
        // Given
        String jsonResponse = """
                {
                    "results": [{
                        "name": "Lyon",
                        "latitude": 45.7578,
                        "longitude": 4.8320,
                        "country": "France"
                    }]
                }
                """;
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(httpResponse);
        when(httpResponse.body()).thenReturn(jsonResponse);

        // When
        GeoLocation location = weatherService.geocode("Lyon");

        // Then
        assertThat(location.name()).isEqualTo("Lyon");
        assertThat(location.latitude()).isEqualTo(45.7578);
        assertThat(location.longitude()).isEqualTo(4.8320);
        assertThat(location.country()).isEqualTo("France");
    }

    @Test
    void geocode_WithUnknownCity_ShouldThrowIOException() throws Exception {
        // Given
        String jsonResponse = """
                {
                    "results": []
                }
                """;
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(httpResponse);
        when(httpResponse.body()).thenReturn(jsonResponse);

        // When / Then
        assertThatThrownBy(() -> weatherService.geocode("XyzNonExistentCity"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Ville introuvable");
    }

    @Test
    void geocode_WithNullResults_ShouldThrowIOException() throws Exception {
        // Given
        String jsonResponse = "{}";
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(httpResponse);
        when(httpResponse.body()).thenReturn(jsonResponse);

        // When / Then
        assertThatThrownBy(() -> weatherService.geocode("Unknown"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Ville introuvable");
    }

    // ── Device location ─────────────────────────────────────────────────────

    @Test
    void getDeviceLocation_ShouldReturnAndCacheLocation() throws Exception {
        // Given
        String jsonResponse = """
                {
                    "city": "Paris",
                    "latitude": 48.8566,
                    "longitude": 2.3522,
                    "country_name": "France"
                }
                """;
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(httpResponse);
        when(httpResponse.body()).thenReturn(jsonResponse);

        // When
        GeoLocation first = weatherService.getDeviceLocation();
        GeoLocation second = weatherService.getDeviceLocation();

        // Then
        assertThat(first.name()).isEqualTo("Paris");
        assertThat(first.latitude()).isEqualTo(48.8566);
        assertThat(second).isSameAs(first); // cached — same object reference
    }

    @Test
    void getDeviceLocation_WithMissingFields_ShouldUseDefaults() throws Exception {
        // Given
        String jsonResponse = """
                {
                    "latitude": 48.0,
                    "longitude": 2.0
                }
                """;
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(httpResponse);
        when(httpResponse.body()).thenReturn(jsonResponse);

        // When
        GeoLocation location = weatherService.getDeviceLocation();

        // Then
        assertThat(location.name()).isEqualTo("Inconnu");
        assertThat(location.country()).isEmpty();
    }

    // ── Weather ─────────────────────────────────────────────────────────────

    @Test
    void getWeather_ShouldParseCurrentAndForecast() throws Exception {
        // Given
        String jsonResponse = """
                {
                    "current": {
                        "temperature_2m": 18.5,
                        "relative_humidity_2m": 65,
                        "wind_speed_10m": 12.3,
                        "weather_code": 2
                    },
                    "daily": {
                        "time": ["2026-03-21", "2026-03-22"],
                        "temperature_2m_min": [8.0, 10.0],
                        "temperature_2m_max": [19.0, 21.0],
                        "weather_code": [0, 61]
                    }
                }
                """;
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(httpResponse);
        when(httpResponse.body()).thenReturn(jsonResponse);

        // When
        WeatherResult result = weatherService.getWeather(45.75, 4.83, 2);

        // Then
        assertThat(result.current().temperature()).isEqualTo(18.5);
        assertThat(result.current().humidity()).isEqualTo(65);
        assertThat(result.current().windSpeed()).isEqualTo(12.3);
        assertThat(result.current().description()).isEqualTo("Partiellement nuageux");

        assertThat(result.forecast()).hasSize(2);
        assertThat(result.forecast().get(0).description()).isEqualTo("Ciel dégagé");
        assertThat(result.forecast().get(1).description()).isEqualTo("Pluie légère");
        assertThat(result.forecast().get(0).tempMin()).isEqualTo(8.0);
        assertThat(result.forecast().get(0).tempMax()).isEqualTo(19.0);
    }

    @Test
    void getWeather_WithUnknownWeatherCode_ShouldReturnDefaultDescription() throws Exception {
        // Given
        String jsonResponse = """
                {
                    "current": {
                        "temperature_2m": 15.0,
                        "relative_humidity_2m": 50,
                        "wind_speed_10m": 5.0,
                        "weather_code": 999
                    },
                    "daily": {
                        "time": ["2026-03-21"],
                        "temperature_2m_min": [10.0],
                        "temperature_2m_max": [20.0],
                        "weather_code": [999]
                    }
                }
                """;
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(httpResponse);
        when(httpResponse.body()).thenReturn(jsonResponse);

        // When
        WeatherResult result = weatherService.getWeather(48.85, 2.35, 1);

        // Then
        assertThat(result.current().description()).contains("Conditions inconnues");
        assertThat(result.current().description()).contains("999");
    }

    // ── getWeatherForCity ───────────────────────────────────────────────────

    @Test
    void getWeatherForCity_WithCity_ShouldGeocodeAndFetchWeather() throws Exception {
        // Given: two HTTP calls — geocode then weather
        String geocodeResponse = """
                {
                    "results": [{
                        "name": "Marseille",
                        "latitude": 43.2965,
                        "longitude": 5.3698,
                        "country": "France"
                    }]
                }
                """;
        String weatherResponse = """
                {
                    "current": {
                        "temperature_2m": 22.0,
                        "relative_humidity_2m": 55,
                        "wind_speed_10m": 15.0,
                        "weather_code": 0
                    },
                    "daily": {
                        "time": ["2026-03-21"],
                        "temperature_2m_min": [14.0],
                        "temperature_2m_max": [23.0],
                        "weather_code": [0]
                    }
                }
                """;
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(httpResponse);
        when(httpResponse.body())
                .thenReturn(geocodeResponse)
                .thenReturn(weatherResponse);

        // When
        WeatherResult result = weatherService.getWeatherForCity("Marseille", 1);

        // Then
        assertThat(result.locationName()).isEqualTo("Marseille");
        assertThat(result.current().temperature()).isEqualTo(22.0);
        assertThat(result.current().description()).isEqualTo("Ciel dégagé");
    }

    @Test
    void getWeatherForCity_WithBlankCity_ShouldUseDeviceLocation() throws Exception {
        // Given: two HTTP calls — device location then weather
        String locationResponse = """
                {
                    "city": "Paris",
                    "latitude": 48.8566,
                    "longitude": 2.3522,
                    "country_name": "France"
                }
                """;
        String weatherResponse = """
                {
                    "current": {
                        "temperature_2m": 12.0,
                        "relative_humidity_2m": 80,
                        "wind_speed_10m": 8.0,
                        "weather_code": 3
                    },
                    "daily": {
                        "time": ["2026-03-21"],
                        "temperature_2m_min": [7.0],
                        "temperature_2m_max": [13.0],
                        "weather_code": [3]
                    }
                }
                """;
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(httpResponse);
        when(httpResponse.body())
                .thenReturn(locationResponse)
                .thenReturn(weatherResponse);

        // When
        WeatherResult result = weatherService.getWeatherForCity("", 1);

        // Then
        assertThat(result.locationName()).isEqualTo("Paris");
        assertThat(result.current().description()).isEqualTo("Couvert");
    }
}
