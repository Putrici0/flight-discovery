package flightdiscovery.paull.api.recommendation;

public record RecommendationRouteDiscardDebug(
        String routeId,
        String routeName,
        String stage,
        String reason,
        double estimatedTimeMinutes,
        double limitMinutes
) {
}
