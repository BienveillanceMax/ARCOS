package org.arcos.Tools.Actions;

import org.arcos.Tools.WeatherTool.WeatherService;
import org.arcos.Tools.WeatherTool.WeatherService.DailyForecast;
import org.arcos.Tools.WeatherTool.WeatherService.WeatherResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
public class WeatherActions {

    private final WeatherService weatherService;

    public WeatherActions(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @Tool(name = "Consulter_la_meteo",
          description = "Consulte la météo actuelle et les prévisions pour une ville donnée. "
                      + "Par défaut, utilise la localisation de l'appareil si aucune ville n'est précisée. "
                      + "forecastDays contrôle le nombre de jours de prévision (défaut 3, max 16). "
                      + "Ne nécessite pas de recherche web.")
    public ActionResult getWeather(String city, int forecastDays) {
        long startTime = System.currentTimeMillis();

        if (forecastDays <= 0) {
            forecastDays = 3;
        } else if (forecastDays > 16) {
            forecastDays = 16;
        }

        log.info("Consultation météo — ville: {}, jours: {}", city != null ? city : "(localisation auto)", forecastDays);

        try {
            WeatherResult result = weatherService.getWeatherForCity(city, forecastDays);
            String formatted = formatWeatherResult(result);

            return ActionResult.success(List.of(formatted), "Météo récupérée")
                    .addMetadata("city", result.locationName())
                    .addMetadata("forecastDays", forecastDays)
                    .withExecutionTime(System.currentTimeMillis() - startTime);

        } catch (IOException e) {
            log.error("Erreur météo : {}", e.getMessage());
            return ActionResult.failure("Impossible de récupérer la météo : " + e.getMessage(), e)
                    .withExecutionTime(System.currentTimeMillis() - startTime);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Requête météo interrompue : {}", e.getMessage());
            return ActionResult.failure("Requête météo interrompue : " + e.getMessage(), e)
                    .withExecutionTime(System.currentTimeMillis() - startTime);
        }
    }

    private String formatWeatherResult(WeatherResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Météo à ").append(result.locationName()).append(" :\n");
        sb.append(String.format("Actuellement : %.1f°C, %s, vent %.1f km/h, humidité %d%%\n",
                result.current().temperature(),
                result.current().description(),
                result.current().windSpeed(),
                result.current().humidity()));

        for (DailyForecast day : result.forecast()) {
            sb.append(String.format("%s : %.1f°C — %.1f°C, %s\n",
                    day.date(), day.tempMin(), day.tempMax(), day.description()));
        }

        return sb.toString().trim();
    }
}
