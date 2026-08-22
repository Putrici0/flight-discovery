package flightdiscovery.paull.api.recommendation;

import java.util.List;

public record RecommendationResponse(
        List<RecommendedRouteResponse> recommendations,
        List<String> warnings,
        RecommendationDebugInfo debugInfo
) {
}
