package org.arcos.UnitTests.Tools;

import org.arcos.Tools.Actions.ActionResult;
import org.arcos.Tools.Actions.WeatherActions;
import org.arcos.Tools.WeatherTool.WeatherService;
import org.arcos.Tools.WeatherTool.WeatherService.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests pour Consulter_la_meteo (WeatherActions + WeatherService).
 *
 * Valide :
 * - AC4 : meteo avec ville => previsions retournees
 * - AC6 : Open-Meteo indisponible => degradation gracieuse
 *
 * Action layer tested here (WeatherActions avec WeatherService mocke).
 * HTTP layer tested in WeatherServiceHttpTest (same package as WeatherService).
 */
@ExtendWith(MockitoExtension.class)
class WeatherToolTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // WeatherActions — action layer
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("WeatherActions — Consulter_la_meteo")
    class WeatherActionsTests {

        @Mock
        private WeatherService weatherService;

        private WeatherActions weatherActions;

        @BeforeEach
        void setUp() {
            weatherActions = new WeatherActions(weatherService);
        }

        @Test
        @DisplayName("Given valid city, When Open-Meteo responds, Then formatted forecast is returned")
        void getWeather_WithValidCity_ShouldReturnFormattedForecast() throws Exception {
            // Given
            WeatherResult mockResult = new WeatherResult(
                    "Paris",
                    new CurrentWeather(22.3, 55, 15.7, "Partiellement nuageux"),
                    List.of(
                            new DailyForecast("2026-03-26", 14.0, 24.0, "Ciel dégagé"),
                            new DailyForecast("2026-03-27", 12.0, 20.0, "Pluie légère"),
                            new DailyForecast("2026-03-28", 10.0, 18.0, "Couvert")
                    )
            );
            when(weatherService.getWeatherForCity("Paris", 3)).thenReturn(mockResult);

            // When
            ActionResult result = weatherActions.getWeather("Paris", 3);

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).isEqualTo("Météo récupérée");
            List<String> data = (List<String>) result.getData();
            assertThat(data).hasSize(1);
            String forecast = data.getFirst();
            assertThat(forecast).contains("Paris");
            // String.format uses JVM default locale — accept both . and ,
            assertThat(forecast).containsPattern("22[.,]3°C");
            assertThat(forecast).contains("Partiellement nuageux");
            assertThat(forecast).containsPattern("15[.,]7 km/h");
            assertThat(forecast).contains("55%");
            assertThat(forecast).contains("2026-03-26");
            assertThat(forecast).contains("Ciel dégagé");
            assertThat(result.getMetadata()).containsEntry("city", "Paris");
            assertThat(result.getMetadata()).containsEntry("forecastDays", 3);
        }

        @Test
        @DisplayName("Given forecastDays > 16, When queried, Then clamped to 16")
        void getWeather_WithExcessiveForecastDays_ShouldClampTo16() throws Exception {
            // Given
            WeatherResult mockResult = new WeatherResult(
                    "Lyon", new CurrentWeather(15.0, 60, 10.0, "Couvert"), List.of());
            when(weatherService.getWeatherForCity("Lyon", 16)).thenReturn(mockResult);

            // When
            ActionResult result = weatherActions.getWeather("Lyon", 25);

            // Then
            assertThat(result.isSuccess()).isTrue();
            verify(weatherService).getWeatherForCity("Lyon", 16);
        }

        @Test
        @DisplayName("Given negative forecastDays, When queried, Then defaults to 3")
        void getWeather_WithNegativeForecastDays_ShouldDefaultToThree() throws Exception {
            // Given
            WeatherResult mockResult = new WeatherResult(
                    "Lyon", new CurrentWeather(15.0, 60, 10.0, "Couvert"), List.of());
            when(weatherService.getWeatherForCity("Lyon", 3)).thenReturn(mockResult);

            // When
            ActionResult result = weatherActions.getWeather("Lyon", -5);

            // Then
            verify(weatherService).getWeatherForCity("Lyon", 3);
        }

        // ── AC6 : degradation gracieuse ─────────────────────────────────────

        @Test
        @DisplayName("Given unknown city, When IOException, Then failure with explicit message")
        void getWeather_WhenCityNotFound_ShouldReturnFailure() throws Exception {
            // Given
            when(weatherService.getWeatherForCity("VilleInexistante", 3))
                    .thenThrow(new IOException("Ville introuvable : VilleInexistante"));

            // When
            ActionResult result = weatherActions.getWeather("VilleInexistante", 3);

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("Impossible de récupérer la météo");
            assertThat(result.getMessage()).contains("Ville introuvable");
            assertThat(result.getExecutionTimeMs()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("Given Open-Meteo down, When queried, Then failure with explicit message")
        void getWeather_WhenOpenMeteoDown_ShouldReturnFailure() throws Exception {
            // Given
            when(weatherService.getWeatherForCity("Paris", 3))
                    .thenThrow(new IOException("Connection timed out"));

            // When
            ActionResult result = weatherActions.getWeather("Paris", 3);

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("Impossible de récupérer la météo");
        }

        @Test
        @DisplayName("Given interrupted request, When queried, Then failure and interrupt restored")
        void getWeather_WhenInterrupted_ShouldReturnFailureAndRestoreFlag() throws Exception {
            // Given
            when(weatherService.getWeatherForCity("Paris", 3))
                    .thenThrow(new InterruptedException("Thread interrupted"));

            // When
            ActionResult result = weatherActions.getWeather("Paris", 3);

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("interrompue");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();

            // Clean up for test runner
            Thread.interrupted();
        }

        @Test
        @DisplayName("Circuit breaker fallback should return failure ActionResult")
        void getWeatherFallback_ShouldReturnFailure() {
            // When
            ActionResult result = weatherActions.getWeatherFallback(
                    "Paris", 3, new RuntimeException("Open-Meteo overloaded"));

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("temporairement indisponible");
        }
    }

}
