package org.arcos.UnitTests.Tools;

import org.arcos.Tools.Actions.ActionResult;
import org.arcos.Tools.Actions.WeatherActions;
import org.arcos.Tools.WeatherTool.WeatherService;
import org.arcos.Tools.WeatherTool.WeatherService.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeatherActionsTest {

    @Mock
    private WeatherService weatherService;

    private WeatherActions weatherActions;

    @BeforeEach
    void setUp() {
        weatherActions = new WeatherActions(weatherService);
    }

    @Test
    void getWeather_WithValidCity_ShouldReturnFormattedWeather() throws Exception {
        // Given
        WeatherResult mockResult = new WeatherResult(
                "Lyon",
                new CurrentWeather(18.5, 65, 12.3, "Partiellement nuageux"),
                List.of(new DailyForecast("2026-03-02", 8.0, 19.0, "Ciel dégagé"))
        );
        when(weatherService.getWeatherForCity("Lyon", 3)).thenReturn(mockResult);

        // When
        ActionResult result = weatherActions.getWeather("Lyon", 3);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Météo récupérée");
        verify(weatherService).getWeatherForCity("Lyon", 3);
    }

    @Test
    void getWeather_WithNullCity_ShouldUseDeviceLocation() throws Exception {
        // Given
        WeatherResult mockResult = new WeatherResult(
                "Paris",
                new CurrentWeather(15.0, 70, 8.0, "Couvert"),
                List.of(new DailyForecast("2026-03-02", 10.0, 16.0, "Pluie légère"))
        );
        when(weatherService.getWeatherForCity(null, 3)).thenReturn(mockResult);

        // When
        ActionResult result = weatherActions.getWeather(null, 3);

        // Then
        assertThat(result.isSuccess()).isTrue();
        verify(weatherService).getWeatherForCity(null, 3);
    }

    @Test
    void getWeather_WithBlankCity_ShouldUseDeviceLocation() throws Exception {
        // Given
        WeatherResult mockResult = new WeatherResult(
                "Paris",
                new CurrentWeather(15.0, 70, 8.0, "Couvert"),
                List.of(new DailyForecast("2026-03-02", 10.0, 16.0, "Pluie légère"))
        );
        when(weatherService.getWeatherForCity("  ", 3)).thenReturn(mockResult);

        // When
        ActionResult result = weatherActions.getWeather("  ", 3);

        // Then
        assertThat(result.isSuccess()).isTrue();
        verify(weatherService).getWeatherForCity("  ", 3);
    }

    @Test
    void getWeather_WithZeroForecastDays_ShouldDefaultToThree() throws Exception {
        // Given
        WeatherResult mockResult = new WeatherResult(
                "Lyon",
                new CurrentWeather(18.5, 65, 12.3, "Ciel dégagé"),
                List.of(new DailyForecast("2026-03-02", 8.0, 19.0, "Ciel dégagé"))
        );
        when(weatherService.getWeatherForCity("Lyon", 3)).thenReturn(mockResult);

        // When
        ActionResult result = weatherActions.getWeather("Lyon", 0);

        // Then
        assertThat(result.isSuccess()).isTrue();
        verify(weatherService).getWeatherForCity("Lyon", 3);
    }

    @Test
    void getWeather_WhenServiceThrowsIOException_ShouldReturnFailure() throws Exception {
        // Given
        when(weatherService.getWeatherForCity("Unknown", 3))
                .thenThrow(new IOException("Ville introuvable : Unknown"));

        // When
        ActionResult result = weatherActions.getWeather("Unknown", 3);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Impossible de récupérer la météo");
        assertThat(result.getMessage()).contains("Ville introuvable");
    }
}
