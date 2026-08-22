package flightdiscovery.paull.api.recommendation;

import java.util.List;

import flightdiscovery.paull.domain.model.RouteScore;
import flightdiscovery.paull.domain.model.RouteType;
import flightdiscovery.paull.domain.model.Waypoint;

public record RecommendedRouteResponse(
        String id,
        String name,
        String description,
        RouteType routeType,
        List<Waypoint> waypoints,
        double approximateDistanceKm,
        double estimatedTimeMinutes,
        double estimatedTimeHours,
        double estimatedFuelLiters,
        double fuelPricePerLiter,
        String fuelPriceSource,
        double estimatedCost,
        double totalScore,
        double weatherScore,
        double windKmh,
        double cloudCoverPercent,
        double precipitationProbability,
        double visibilityKm,
        RouteScore scoreBreakdown,
        String explanation,
        List<String> warnings
) {
}
