package flightdiscovery.paull.api.recommendation;

import java.util.List;

import flightdiscovery.paull.domain.model.Aircraft;

public record RecommendationDevelopmentDebugResponse(
        RecommendationRequest request,
        Aircraft aircraft,
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
