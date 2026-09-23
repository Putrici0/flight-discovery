package flightdiscovery.paull.application.recommendation;

import org.springframework.stereotype.Service;

import flightdiscovery.paull.api.recommendation.RecommendedRouteResponse;
import flightdiscovery.paull.api.recommendation.RouteDurationCategory;
import flightdiscovery.paull.domain.model.Aircraft;

@Service
public class RecommendationTimeService {

    static final double MAX_ALLOWED_TIME_OVERRUN_RATIO = 1.25;
    private static final double LOW_TIME_MARGIN_RATIO = 0.90;

    public double usefulAvailableTimeMinutes(int availableFlightTimeMinutes, Aircraft aircraft, int safetyMarginPercent) {
        double availableAfterReserveMinutes = Math.max(
                0.0,
                availableFlightTimeMinutes - aircraft.recommendedReserveMinutes()
        );

        return availableAfterReserveMinutes * (100.0 - safetyMarginPercent) / 100.0;
    }

    public boolean isWithinAllowedTime(RecommendedRouteResponse recommendation, double availableTimeMinutes) {
        return recommendation.estimatedTimeMinutes() <= allowedTimeLimitMinutes(availableTimeMinutes);
    }

    public double allowedTimeLimitMinutes(double availableTimeMinutes) {
        return availableTimeMinutes * MAX_ALLOWED_TIME_OVERRUN_RATIO;
    }

    public RouteDurationCategory routeDurationCategory(double estimatedTimeMinutes, double usefulAvailableTimeMinutes) {
        if (usefulAvailableTimeMinutes <= 0.0) {
            return RouteDurationCategory.TOO_LONG;
        }

        double usageRatio = estimatedTimeMinutes / usefulAvailableTimeMinutes;

        if (usageRatio < 0.4) {
            return RouteDurationCategory.TOO_SHORT;
        }

        if (usageRatio < 0.7) {
            return RouteDurationCategory.SHORT;
        }

        if (usageRatio < LOW_TIME_MARGIN_RATIO) {
            return RouteDurationCategory.GOOD_FIT;
        }

        if (usageRatio <= 1.0) {
            return RouteDurationCategory.LONG;
        }

        if (usageRatio <= MAX_ALLOWED_TIME_OVERRUN_RATIO) {
            return RouteDurationCategory.SLIGHTLY_OVER_TIME;
        }

        return RouteDurationCategory.TOO_LONG;
    }

    public String timeFitText(double estimatedTimeMinutes, double availableTimeMinutes) {
        if (availableTimeMinutes <= 0.0) {
            return "no puede evaluarse contra el tiempo disponible";
        }

        double usageRatio = estimatedTimeMinutes / availableTimeMinutes;

        if (usageRatio < 0.7) {
            return "aprovecha poco el tiempo disponible";
        }

        if (usageRatio <= 1.0) {
            return "aprovecha bien el tiempo disponible";
        }

        if (usageRatio <= MAX_ALLOWED_TIME_OVERRUN_RATIO) {
            return "aprovecha demasiado el tiempo disponible";
        }

        return "supera demasiado el tiempo disponible";
    }
}
