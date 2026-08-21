package flightdiscovery.paull.domain.model;

import java.util.List;

public record RouteRecommendationResponse(
        RouteRecommendationRequest request,
        List<RouteRecommendation> recommendations
) {
}
