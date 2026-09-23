package flightdiscovery.paull.application.recommendation;

import flightdiscovery.paull.api.recommendation.RecommendedRouteResponse;
import flightdiscovery.paull.domain.model.FlightRoute;

record ScoredRoute(
        FlightRoute route,
        RecommendedRouteResponse recommendation
) {
}
