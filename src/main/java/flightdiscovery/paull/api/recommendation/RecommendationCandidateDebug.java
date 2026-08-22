package flightdiscovery.paull.api.recommendation;

import flightdiscovery.paull.domain.model.RouteType;

public record RecommendationCandidateDebug(
        String routeId,
        String routeName,
        RouteType routeType,
        double estimatedTimeMinutes,
        Double totalScore,
        Double timeFitScore,
        Double costScore,
        boolean discarded,
        String discardStage,
        String discardReason
) {
}
