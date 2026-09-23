package flightdiscovery.paull.application.recommendation;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import flightdiscovery.paull.domain.model.Airport;
import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.RouteWeatherSummary;
import flightdiscovery.paull.domain.model.WeatherData;
import flightdiscovery.paull.domain.model.Waypoint;
import flightdiscovery.paull.domain.weather.WeatherService;
import flightdiscovery.paull.domain.weather.WeatherServiceException;

@Service
public class RouteWeatherSummaryService {

    public RouteWeatherSummary routeWeatherSummary(
            FlightRoute route,
            Airport departureAirport,
            LocalDateTime plannedDepartureDateTime,
            WeatherData fallbackWeatherData,
            WeatherService activeWeatherService
    ) {
        List<WeatherData> pointWeatherData = routeWeatherPoints(route, departureAirport).stream()
                .map(point -> weatherForPoint(point, plannedDepartureDateTime, fallbackWeatherData, activeWeatherService))
                .toList();

        return new RouteWeatherSummary(
                round(pointWeatherData.stream().mapToDouble(WeatherData::windKmh).average().orElse(fallbackWeatherData.windKmh())),
                round(pointWeatherData.stream().mapToDouble(WeatherData::windKmh).max().orElse(fallbackWeatherData.windKmh())),
                round(pointWeatherData.stream().mapToDouble(WeatherData::cloudCoverPercent).average().orElse(fallbackWeatherData.cloudCoverPercent())),
                round(pointWeatherData.stream().mapToDouble(WeatherData::precipitationProbability).max().orElse(fallbackWeatherData.precipitationProbability())),
                round(pointWeatherData.stream().mapToDouble(WeatherData::visibilityKm).min().orElse(fallbackWeatherData.visibilityKm())),
                round(pointWeatherData.stream().mapToDouble(WeatherData::temperatureCelsius).average().orElse(fallbackWeatherData.temperatureCelsius())),
                round(weatherScore(
                        pointWeatherData.stream().mapToDouble(WeatherData::windKmh).average().orElse(fallbackWeatherData.windKmh()),
                        pointWeatherData.stream().mapToDouble(WeatherData::windKmh).max().orElse(fallbackWeatherData.windKmh()),
                        pointWeatherData.stream().mapToDouble(WeatherData::cloudCoverPercent).average().orElse(fallbackWeatherData.cloudCoverPercent()),
                        pointWeatherData.stream().mapToDouble(WeatherData::precipitationProbability).max().orElse(fallbackWeatherData.precipitationProbability()),
                        pointWeatherData.stream().mapToDouble(WeatherData::visibilityKm).min().orElse(fallbackWeatherData.visibilityKm())
                )),
                pointWeatherData.stream().anyMatch(weatherData -> !weatherData.isMock()) ? "open-meteo" : "mock",
                pointWeatherData.stream().allMatch(WeatherData::isMock)
        );
    }

    public List<Waypoint> routeWeatherPoints(FlightRoute route, Airport departureAirport) {
        Map<String, Waypoint> selectedPoints = new LinkedHashMap<>();
        addWeatherPoint(selectedPoints, airportWaypoint(departureAirport));

        if (!route.waypoints().isEmpty()) {
            addWeatherPoint(selectedPoints, route.waypoints().getFirst());
            addWeatherPoint(selectedPoints, route.waypoints().getLast());
        }

        return selectedPoints.values().stream()
                .limit(3)
                .toList();
    }

    private WeatherData weatherForPoint(
            Waypoint point,
            LocalDateTime plannedDepartureDateTime,
            WeatherData fallbackWeatherData,
            WeatherService activeWeatherService
    ) {
        try {
            return activeWeatherService.weatherFor(point.latitude(), point.longitude(), plannedDepartureDateTime);
        } catch (WeatherServiceException exception) {
            return fallbackWeatherData;
        }
    }

    private void addWeatherPoint(Map<String, Waypoint> points, Waypoint point) {
        points.putIfAbsent(point.latitude() + ":" + point.longitude(), point);
    }

    private Waypoint airportWaypoint(Airport airport) {
        return new Waypoint(airport.code(), airport.latitude(), airport.longitude());
    }

    private double weatherScore(
            double averageWindKmh,
            double maxWindKmh,
            double averageCloudCoverPercent,
            double maxPrecipitationProbability,
            double minVisibilityKm
    ) {
        double windScore = 100.0 - Math.min(100.0, ((averageWindKmh * 0.65) + (maxWindKmh * 0.35)) / 45.0 * 100.0);
        double cloudScore = averageCloudCoverPercent <= 55.0
                ? 100.0 - Math.abs(averageCloudCoverPercent - 30.0) * 0.7
                : 82.5 - (averageCloudCoverPercent - 55.0) * 1.5;
        double precipitationScore = 100.0 - maxPrecipitationProbability * 1.45;
        double visibilityScore = minVisibilityKm >= 20.0
                ? 100.0
                : Math.max(0.0, minVisibilityKm / 20.0 * 100.0);

        return clampScore(windScore * 0.30
                + cloudScore * 0.20
                + precipitationScore * 0.35
                + visibilityScore * 0.15);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double clampScore(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }
}
