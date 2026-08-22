package flightdiscovery.paull.domain.weather;

import org.springframework.stereotype.Service;

import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.WeatherData;

@Service
public class MockWeatherService implements WeatherService {

    @Override
    public WeatherData weatherFor(FlightRoute route) {
        int seed = Math.abs(route.id().hashCode());
        double windKmh = 8.0 + seed % 34;
        double cloudCoverPercent = 10.0 + (seed / 7) % 76;
        double precipitationProbability = (seed / 13) % 65;
        double visibilityKm = 6.0 + (seed / 17) % 24;

        return new WeatherData(
                round(windKmh),
                round(cloudCoverPercent),
                round(precipitationProbability),
                round(visibilityKm),
                round(weatherScore(windKmh, cloudCoverPercent, precipitationProbability, visibilityKm))
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
