package flightdiscovery.paull.domain.scoring;

import org.springframework.stereotype.Service;

import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.RouteScore;

@Service
public class RouteScoringService {

    private static final double MAX_COST_FOR_SCORE = 250.0;

    public RouteScore score(
            FlightRoute route,
            double estimatedTimeMinutes,
            double availableTimeMinutes,
            double estimatedCost,
            String preference,
            double weatherScore
    ) {
        double normalizedWeatherScore = clampScore(weatherScore);
        double timeFitScore = timeFitScore(estimatedTimeMinutes, availableTimeMinutes);
        double preferenceScore = preferenceScore(route, preference);
        double scenicScore = scenicScore(route);
        double costScore = costScore(estimatedCost);
        double totalScore = clampScore(normalizedWeatherScore * 0.20
                + timeFitScore * 0.30
                + preferenceScore * 0.20
                + scenicScore * 0.25
                + costScore * 0.05);

        return new RouteScore(
                round(normalizedWeatherScore),
                round(timeFitScore),
                round(preferenceScore),
                round(scenicScore),
                round(costScore),
                round(totalScore)
        );
    }

    public double timeFitScore(double estimatedTimeMinutes, double availableTimeMinutes) {
        if (availableTimeMinutes <= 0) {
            return 0.0;
        }

        double usageRatio = estimatedTimeMinutes / availableTimeMinutes;

        if (usageRatio <= 0.0) {
            return 0.0;
        }

        if (usageRatio <= 0.85) {
            double distanceFromIdeal = Math.abs(usageRatio - 0.60);
            return Math.max(60.0, 100.0 - distanceFromIdeal * 90.0);
        }

        if (usageRatio <= 1.0) {
            return 78.0 - (usageRatio - 0.85) * 120.0;
        }

        if (usageRatio <= 1.15) {
            return 45.0 - (usageRatio - 1.0) * 160.0;
        }

        if (usageRatio <= 1.5) {
            return 21.0 - (usageRatio - 1.15) * 60.0;
        }

        return 0.0;
    }

    public double costScore(double estimatedCost) {
        if (estimatedCost <= 0.0) {
            return 100.0;
        }

        return clampScore(100.0 - estimatedCost / MAX_COST_FOR_SCORE * 100.0);
    }

    public double scenicScore(FlightRoute route) {
        if (route.scenicScore() <= 10.0) {
            return route.scenicScore() * 10.0;
        }

        return Math.min(100.0, route.scenicScore());
    }

    public double preferenceScore(FlightRoute route, String preference) {
        if (preference == null || preference.isBlank()) {
            return 60.0;
        }

        boolean matchesPreference = route.tags().stream()
                .anyMatch(tag -> tag.equalsIgnoreCase(preference.trim()));

        return matchesPreference ? 100.0 : 45.0;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double clampScore(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }
}
