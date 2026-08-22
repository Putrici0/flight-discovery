package flightdiscovery.paull.domain.scoring;

import org.springframework.stereotype.Service;

import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.RouteScore;

@Service
public class RouteScoringService {

    private static final double MOCK_WEATHER_SCORE = 80.0;

    public RouteScore score(FlightRoute route, double estimatedTimeMinutes, int availableTimeMinutes, double estimatedCost) {
        double weatherScore = MOCK_WEATHER_SCORE;
        double timeFitScore = timeFitScore(estimatedTimeMinutes, availableTimeMinutes);
        double scenicScore = scenicScore(route);
        double costScore = costScore(estimatedCost);
        double totalScore = weatherScore * 0.30
                + timeFitScore * 0.30
                + scenicScore * 0.25
                + costScore * 0.15;

        return new RouteScore(
                round(weatherScore),
                round(timeFitScore),
                round(scenicScore),
                round(costScore),
                round(totalScore)
        );
    }

    public double timeFitScore(double estimatedTimeMinutes, int availableTimeMinutes) {
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
        if (estimatedCost <= 80.0) {
            return 100.0;
        }

        if (estimatedCost >= 220.0) {
            return 45.0;
        }

        return 100.0 - (estimatedCost - 80.0) * 55.0 / 140.0;
    }

    public double scenicScore(FlightRoute route) {
        if (route.scenicScore() <= 10.0) {
            return route.scenicScore() * 10.0;
        }

        return Math.min(100.0, route.scenicScore());
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
