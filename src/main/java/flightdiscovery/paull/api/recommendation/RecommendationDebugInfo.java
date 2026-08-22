package flightdiscovery.paull.api.recommendation;

public record RecommendationDebugInfo(
        int generatedCandidateRoutes,
        int discardedByTimeRoutes,
        int recommendedRoutes
) {
}
