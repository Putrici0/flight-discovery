package flightdiscovery.paull.api.recommendation;

import java.util.List;

import flightdiscovery.paull.domain.model.Aircraft;
import flightdiscovery.paull.domain.model.Waypoint;

public record RecommendationDevelopmentDebugResponse(
        RecommendationRequest request,
        Aircraft aircraft,
        String plannedDepartureDateTime,
        String weatherProviderUsed,
        List<Waypoint> weatherLookupPoints,
        String fuelTypeUsed,
        double fuelPriceUsed,
        String fuelPriceSource,
        boolean fuelPriceIsMock,
        String fuelPriceAirportCode,
        double targetDurationMinutes,
        double usefulAvailableTimeMinutes,
        int compatibleWaypoints,
        int generatedCandidateRoutes,
        int discardedRoutes,
        int finalRoutes,
        List<RecommendationCandidateDebug> candidates,
        List<RecommendationRouteDiscardDebug> discards,
        List<RecommendedRouteResponse> recommendations
) {
}
