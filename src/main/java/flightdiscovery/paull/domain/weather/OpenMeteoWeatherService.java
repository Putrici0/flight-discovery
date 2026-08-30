package flightdiscovery.paull.domain.weather;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.WeatherData;

@Service
@ConditionalOnProperty(name = "weather.provider", havingValue = "open-meteo")
public class OpenMeteoWeatherService implements WeatherService {

    private static final String BASE_URL = "https://api.open-meteo.com";
    private static final String HOURLY_VARIABLES = String.join(",",
            "temperature_2m",
            "wind_speed_10m",
            "cloud_cover",
            "precipitation_probability",
            "visibility"
    );
    private static final double DEFAULT_VISIBILITY_METERS = 10000.0;

    private final RestClient restClient;
    private final WeatherService fallbackWeatherService;
    private final Map<String, WeatherData> cache = new ConcurrentHashMap<>();

    public OpenMeteoWeatherService() {
        this(RestClient.builder().baseUrl(BASE_URL).build(), new MockWeatherService());
    }

    OpenMeteoWeatherService(RestClient restClient, WeatherService fallbackWeatherService) {
        this.restClient = restClient;
        this.fallbackWeatherService = fallbackWeatherService;
    }

    @Override
    public WeatherData weatherFor(FlightRoute route) {
        return weatherFor(route, LocalDateTime.now().withSecond(0).withNano(0));
    }

    @Override
    public WeatherData weatherFor(FlightRoute route, LocalDateTime plannedDepartureDateTime) {
        try {
            return weatherFor(
                    route.departureAirport().latitude(),
                    route.departureAirport().longitude(),
                    plannedDepartureDateTime
            );
        } catch (WeatherServiceException exception) {
            return fallbackWeatherService.weatherFor(route);
        }
    }

    @Override
    public WeatherData weatherFor(
            double latitude,
            double longitude,
            LocalDateTime plannedDepartureDateTime
    ) {
        String cacheKey = cacheKey(latitude, longitude, plannedDepartureDateTime);
        WeatherData cachedWeatherData = cache.get(cacheKey);
        if (cachedWeatherData != null) {
            return cachedWeatherData;
        }

        try {
            OpenMeteoForecastResponse response = fetchForecast(latitude, longitude, plannedDepartureDateTime.toLocalDate());
            WeatherData weatherData = toWeatherData(response, plannedDepartureDateTime);
            cache.put(cacheKey, weatherData);
            return weatherData;
        } catch (RestClientException | IllegalArgumentException | NullPointerException exception) {
            throw new WeatherServiceException("Unable to retrieve weather data from Open-Meteo", exception);
        }
    }

    private OpenMeteoForecastResponse fetchForecast(double latitude, double longitude, LocalDate date) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/forecast")
                        .queryParam("latitude", latitude)
                        .queryParam("longitude", longitude)
                        .queryParam("hourly", HOURLY_VARIABLES)
                        .queryParam("wind_speed_unit", "kmh")
                        .queryParam("timezone", "auto")
                        .queryParam("start_date", date)
                        .queryParam("end_date", date)
                        .build())
                .retrieve()
                .body(OpenMeteoForecastResponse.class);
    }

    private WeatherData toWeatherData(OpenMeteoForecastResponse response, LocalDateTime plannedDepartureDateTime) {
        OpenMeteoHourly hourly = response.hourly();
        int index = closestHourlyIndex(hourly.time(), plannedDepartureDateTime.truncatedTo(ChronoUnit.HOURS));
        double windKmh = requiredValue(hourly.windSpeed10m(), index, "wind_speed_10m");
        double cloudCoverPercent = requiredValue(hourly.cloudCover(), index, "cloud_cover");
        double precipitationProbability = requiredValue(hourly.precipitationProbability(), index, "precipitation_probability");
        double visibilityKm = optionalValue(hourly.visibility(), index, DEFAULT_VISIBILITY_METERS) / 1000.0;
        double temperatureCelsius = requiredValue(hourly.temperature2m(), index, "temperature_2m");

        return new WeatherData(
                round(windKmh),
                round(cloudCoverPercent),
                round(precipitationProbability),
                round(visibilityKm),
                round(temperatureCelsius),
                round(weatherScore(windKmh, cloudCoverPercent, precipitationProbability, visibilityKm)),
                "open-meteo",
                false
        );
    }

    private String cacheKey(double latitude, double longitude, LocalDateTime plannedDepartureDateTime) {
        double roundedLatitude = Math.round(latitude * 10.0) / 10.0;
        double roundedLongitude = Math.round(longitude * 10.0) / 10.0;
        LocalDateTime roundedHour = plannedDepartureDateTime.truncatedTo(ChronoUnit.HOURS);

        return roundedLatitude + ":" + roundedLongitude + ":" + roundedHour;
    }

    private int closestHourlyIndex(List<String> times, LocalDateTime plannedDepartureDateTime) {
        if (times == null || times.isEmpty()) {
            throw new IllegalArgumentException("Open-Meteo response does not include hourly time values");
        }

        int closestIndex = 0;
        long closestDistanceMinutes = Long.MAX_VALUE;
        for (int index = 0; index < times.size(); index++) {
            LocalDateTime time = LocalDateTime.parse(times.get(index));
            long distanceMinutes = Math.abs(ChronoUnit.MINUTES.between(time, plannedDepartureDateTime));
            if (distanceMinutes < closestDistanceMinutes) {
                closestIndex = index;
                closestDistanceMinutes = distanceMinutes;
            }
        }

        return closestIndex;
    }

    private double requiredValue(List<Double> values, int index, String fieldName) {
        if (values == null || values.size() <= index || values.get(index) == null) {
            throw new IllegalArgumentException("Open-Meteo response does not include " + fieldName);
        }

        return values.get(index);
    }

    private double optionalValue(List<Double> values, int index, double defaultValue) {
        if (values == null || values.size() <= index || values.get(index) == null) {
            return defaultValue;
        }

        return values.get(index);
    }

    private double weatherScore(
            double windKmh,
            double cloudCoverPercent,
            double precipitationProbability,
            double visibilityKm
    ) {
        double windScore = 100.0 - Math.min(100.0, windKmh / 45.0 * 100.0);
        double cloudScore = cloudCoverPercent <= 55.0
                ? 100.0 - Math.abs(cloudCoverPercent - 30.0) * 0.7
                : 82.5 - (cloudCoverPercent - 55.0) * 1.5;
        double precipitationScore = 100.0 - precipitationProbability * 1.35;
        double visibilityScore = visibilityKm >= 20.0
                ? 100.0
                : Math.max(0.0, visibilityKm / 20.0 * 100.0);

        return clamp(windScore * 0.30
                + cloudScore * 0.20
                + precipitationScore * 0.30
                + visibilityScore * 0.20);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record OpenMeteoForecastResponse(
            OpenMeteoHourly hourly
    ) {
    }

    private record OpenMeteoHourly(
            List<String> time,
            @com.fasterxml.jackson.annotation.JsonProperty("temperature_2m")
            List<Double> temperature2m,
            @com.fasterxml.jackson.annotation.JsonProperty("wind_speed_10m")
            List<Double> windSpeed10m,
            @com.fasterxml.jackson.annotation.JsonProperty("cloud_cover")
            List<Double> cloudCover,
            @com.fasterxml.jackson.annotation.JsonProperty("precipitation_probability")
            List<Double> precipitationProbability,
            List<Double> visibility
    ) {
    }
}
