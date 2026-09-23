package flightdiscovery.paull.application.recommendation;

import java.util.List;

public record RecommendationSelectionResult(
        List<ScoredRoute> selectedRoutes,
        List<ScoredRoute> similarRoutes
) {
}
