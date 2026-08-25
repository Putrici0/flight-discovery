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
        List<Waypoint> flightPath,
        List<SightseeingManeuverResponse> sightseeingManeuvers,
        double approximateDistanceKm,
        double baseFlightTimeMinutes,
        double sightseeingTimeMinutes,
        String plannedDepartureDateTime,
        double sunAzimuthDegrees,
        double sunExposureScore,
        String sunExposureSummary,
        double estimatedTimeMinutes,
        double estimatedTimeHours,
        RouteDurationCategory routeDurationCategory,
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
