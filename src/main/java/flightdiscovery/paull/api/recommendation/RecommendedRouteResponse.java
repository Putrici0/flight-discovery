package flightdiscovery.paull.api.recommendation;

import java.util.List;

import flightdiscovery.paull.domain.model.RouteScore;
import flightdiscovery.paull.domain.model.RouteType;
import flightdiscovery.paull.domain.model.RouteWeatherSummary;
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
        boolean fuelPriceIsMock,
        String fuelTypeUsed,
        String fuelPriceAirportCode,
        double estimatedCost,
        double totalScore,
        double weatherScore,
        String weatherProvider,
        boolean weatherIsMock,
        double windKmh,
        double cloudCoverPercent,
        double precipitationProbability,
        double visibilityKm,
        double temperatureCelsius,
        RouteWeatherSummary routeWeatherSummary,
        RouteScore scoreBreakdown,
        String explanation,
        List<String> warnings
) {
}
