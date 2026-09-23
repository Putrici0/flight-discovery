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
        double timeFitScore = timeFitScore(estimatedTimeMinutes, availableTimeMinutes, preference);
        double preferenceScore = preferenceScore(route, preference);
        double scenicScore = scenicScore(route);
        double costScore = costScore(estimatedCost);
        double totalScore = clampScore(normalizedWeatherScore * 0.25
                + timeFitScore * 0.35
                + scenicScore * 0.25
                + preferenceScore * 0.10
                + costScore * 0.05);
        if (isInterIslandRoute(route) && !isInterIslandPreference(preference)) {
            totalScore = clampScore(totalScore - 35.0);
        }

        return new RouteScore(
                round(normalizedWeatherScore),
                round(timeFitScore),
                round(preferenceScore),
                round(scenicScore),
                round(costScore),
                round(totalScore)
        );
    }

    public RouteScore score(
            FlightRoute route,
            double estimatedTimeMinutes,
            double availableTimeMinutes,
            double estimatedCost,
            String preference,
            double weatherScore,
            double visualOrientationScore
    ) {
        double normalizedWeatherScore = clampScore(weatherScore);
        double normalizedVisualOrientationScore = clampScore(visualOrientationScore);
        double timeFitScore = timeFitScore(estimatedTimeMinutes, availableTimeMinutes, preference);
        double preferenceScore = preferenceScore(route, preference);
        double scenicScore = scenicScore(route);
        double costScore = costScore(estimatedCost);
        double totalScore = clampScore(normalizedWeatherScore * 0.23
                + timeFitScore * 0.30
                + scenicScore * 0.22
                + normalizedVisualOrientationScore * 0.12
                + preferenceScore * 0.08
                + costScore * 0.05);
        if (isInterIslandRoute(route) && !isInterIslandPreference(preference)) {
            totalScore = clampScore(totalScore - 35.0);
        }

        return new RouteScore(
                round(normalizedWeatherScore),
                round(timeFitScore),
                round(preferenceScore),
                round(scenicScore),
                round(normalizedVisualOrientationScore),
                round(costScore),
                round(totalScore)
        );
    }

    public double timeFitScore(double estimatedTimeMinutes, double availableTimeMinutes) {
        return timeFitScore(estimatedTimeMinutes, availableTimeMinutes, null);
    }

    public double timeFitScore(double estimatedTimeMinutes, double availableTimeMinutes, String preference) {
        if (availableTimeMinutes <= 0) {
            return 0.0;
        }

        double usageRatio = estimatedTimeMinutes / availableTimeMinutes;
        double targetDurationMinutes = availableTimeMinutes * 0.85;

        if (usageRatio <= 0.0) {
            return 0.0;
        }

        if (usageRatio > 1.25) {
            return 0.0;
        }

        if (isShortPreference(preference) && usageRatio < 0.7) {
            return 85.0;
        }

        if (usageRatio < 0.4) {
            return 15.0 + usageRatio / 0.4 * 10.0;
        }

        if (usageRatio < 0.6) {
            return 40.0 + (usageRatio - 0.4) / 0.2 * 15.0;
        }

        if (usageRatio < 0.7) {
            return 55.0 + (usageRatio - 0.6) / 0.1 * 15.0;
        }

        if (usageRatio <= 1.0) {
            double distanceFromTargetRatio = Math.abs(estimatedTimeMinutes - targetDurationMinutes) / availableTimeMinutes;
            return 100.0 - distanceFromTargetRatio / 0.15 * 15.0;
        }

        return 70.0 - (usageRatio - 1.0) / 0.25 * 55.0;
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
        if (preference == null || preference.isBlank() || preference.trim().equalsIgnoreCase("any")) {
            return 60.0;
        }

        String normalizedPreference = preference.trim().toLowerCase();
        boolean exactMatch = route.tags().stream()
                .anyMatch(tag -> tag.equalsIgnoreCase(normalizedPreference));
        if (exactMatch) {
            return 100.0;
        }

        boolean partialMatch = route.tags().stream()
                .map(tag -> tag.trim().toLowerCase())
                .anyMatch(tag -> tag.contains(normalizedPreference) || normalizedPreference.contains(tag));

        return partialMatch ? 70.0 : 35.0;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double clampScore(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    private boolean isShortPreference(String preference) {
        return preference != null && preference.trim().equalsIgnoreCase("short");
    }

    private boolean isInterIslandRoute(FlightRoute route) {
        return route.tags().stream()
                .anyMatch(tag -> tag.equalsIgnoreCase("inter-island") || tag.equalsIgnoreCase("islands"));
    }

    private boolean isInterIslandPreference(String preference) {
        if (preference == null || preference.isBlank()) {
            return false;
        }

        String normalizedPreference = preference.trim().toLowerCase();

        return normalizedPreference.equals("inter-island")
                || normalizedPreference.equals("islands")
                || normalizedPreference.equals("cross-country")
                || normalizedPreference.equals("adventure");
    }
}
