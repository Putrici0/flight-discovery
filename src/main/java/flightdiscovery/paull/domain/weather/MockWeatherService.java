package flightdiscovery.paull.domain.weather;

import java.time.LocalDateTime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.WeatherData;

@Service
@ConditionalOnProperty(name = "weather.provider", havingValue = "mock", matchIfMissing = true)
public class MockWeatherService implements WeatherService {

    @Override
    public WeatherData weatherFor(FlightRoute route) {
        int seed = Math.abs(route.id().hashCode());

        return weatherFromSeed(seed);
    }

    @Override
    public WeatherData weatherFor(double latitude, double longitude, LocalDateTime plannedDepartureDateTime) {
        int seed = Math.abs((latitude + ":" + longitude + ":" + plannedDepartureDateTime).hashCode());

        return weatherFromSeed(seed);
    }

    private WeatherData weatherFromSeed(int seed) {
        double windKmh = 8.0 + seed % 34;
        double cloudCoverPercent = 10.0 + (seed / 7) % 76;
        double precipitationProbability = (seed / 13) % 65;
        double visibilityKm = 6.0 + (seed / 17) % 24;
        double temperatureCelsius = 12.0 + (seed / 23) % 22;

        return new WeatherData(
                round(windKmh),
                round(cloudCoverPercent),
                round(precipitationProbability),
                round(visibilityKm),
                round(temperatureCelsius),
                round(weatherScore(windKmh, cloudCoverPercent, precipitationProbability, visibilityKm)),
                "mock",
                true
        );
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
}
