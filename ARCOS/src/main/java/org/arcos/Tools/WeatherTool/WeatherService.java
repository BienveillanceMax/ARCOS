package org.arcos.Tools.WeatherTool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Map;

@Slf4j
@Service
public class WeatherService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private GeoLocation cachedDeviceLocation;

    private static final Map<Integer, String> WMO_CODES = Map.ofEntries(
            Map.entry(0, "Ciel dégagé"),
            Map.entry(1, "Majoritairement dégagé"),
            Map.entry(2, "Partiellement nuageux"),
            Map.entry(3, "Couvert"),
            Map.entry(45, "Brouillard"),
            Map.entry(48, "Brouillard givrant"),
            Map.entry(51, "Bruine légère"),
            Map.entry(53, "Bruine modérée"),
            Map.entry(55, "Bruine dense"),
            Map.entry(61, "Pluie légère"),
            Map.entry(63, "Pluie modérée"),
            Map.entry(65, "Pluie forte"),
            Map.entry(71, "Neige légère"),
            Map.entry(73, "Neige modérée"),
            Map.entry(75, "Neige forte"),
            Map.entry(80, "Averses légères"),
            Map.entry(81, "Averses modérées"),
            Map.entry(82, "Averses violentes"),
            Map.entry(95, "Orage"),
            Map.entry(96, "Orage avec grêle légère"),
            Map.entry(99, "Orage avec grêle forte")
    );

    public WeatherService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    // Constructor for testing
    WeatherService(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public GeoLocation geocode(String city) throws IOException, InterruptedException {
        String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8);
        String url = "https://geocoding-api.open-meteo.com/v1/search?name=" + encodedCity + "&count=1&language=fr";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "ARCOS/1.0")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = objectMapper.readTree(response.body());

        JsonNode results = root.get("results");
        if (results == null || results.isEmpty()) {
            throw new IOException("Ville introuvable : " + city);
        }

        JsonNode first = results.get(0);
        return new GeoLocation(
                first.get("name").asText(),
                first.get("latitude").asDouble(),
                first.get("longitude").asDouble(),
                first.has("country") ? first.get("country").asText() : ""
        );
    }

    public GeoLocation getDeviceLocation() throws IOException, InterruptedException {
        if (cachedDeviceLocation != null) {
            return cachedDeviceLocation;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://ipapi.co/json/"))
                .header("User-Agent", "ARCOS/1.0")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = objectMapper.readTree(response.body());

        cachedDeviceLocation = new GeoLocation(
                root.has("city") ? root.get("city").asText() : "Inconnu",
                root.get("latitude").asDouble(),
                root.get("longitude").asDouble(),
                root.has("country_name") ? root.get("country_name").asText() : ""
        );

        return cachedDeviceLocation;
    }

    public WeatherResult getWeather(double lat, double lon, int forecastDays)
            throws IOException, InterruptedException {
        String url = String.format(
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                + "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                + "&timezone=auto&forecast_days=%d",
                lat, lon, forecastDays
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "ARCOS/1.0")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = objectMapper.readTree(response.body());

        JsonNode current = root.get("current");
        CurrentWeather currentWeather = new CurrentWeather(
                current.get("temperature_2m").asDouble(),
                current.get("relative_humidity_2m").asInt(),
                current.get("wind_speed_10m").asDouble(),
                describeWeatherCode(current.get("weather_code").asInt())
        );

        JsonNode daily = root.get("daily");
        List<DailyForecast> forecasts = new ArrayList<>();
        JsonNode dates = daily.get("time");
        JsonNode tempMin = daily.get("temperature_2m_min");
        JsonNode tempMax = daily.get("temperature_2m_max");
        JsonNode weatherCodes = daily.get("weather_code");

        for (int i = 0; i < dates.size(); i++) {
            forecasts.add(new DailyForecast(
                    dates.get(i).asText(),
                    tempMin.get(i).asDouble(),
                    tempMax.get(i).asDouble(),
                    describeWeatherCode(weatherCodes.get(i).asInt())
            ));
        }

        return new WeatherResult(null, currentWeather, forecasts);
    }

    public WeatherResult getWeatherForCity(String city, int forecastDays)
            throws IOException, InterruptedException {
        GeoLocation location;
        if (city == null || city.isBlank()) {
            location = getDeviceLocation();
        } else {
            location = geocode(city);
        }

        WeatherResult result = getWeather(location.latitude(), location.longitude(), forecastDays);
        return new WeatherResult(location.name(), result.current(), result.forecast());
    }

    private String describeWeatherCode(int code) {
        return WMO_CODES.getOrDefault(code, "Conditions inconnues (" + code + ")");
    }

    // Inner records

    public record GeoLocation(String name, double latitude, double longitude, String country) {}

    public record WeatherResult(String locationName, CurrentWeather current, List<DailyForecast> forecast) {}

    public record CurrentWeather(double temperature, int humidity, double windSpeed, String description) {}

    public record DailyForecast(String date, double tempMin, double tempMax, String description) {}
}
