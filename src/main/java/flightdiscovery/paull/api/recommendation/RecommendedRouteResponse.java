package flightdiscovery.paull.api.recommendation;

import java.util.List;

import flightdiscovery.paull.domain.model.RouteScore;
import flightdiscovery.paull.domain.model.Waypoint;

public record RecommendedRouteResponse(
        String id,
        String name,
        String description,
        List<Waypoint> waypoints,
        double approximateDistanceKm,
        double estimatedTimeMinutes,
        double estimatedTimeHours,
        double estimatedFuelLiters,
        double estimatedCost,
        double totalScore,
        RouteScore scoreBreakdown,
        String explanation,
        List<String> warnings
) {
}
