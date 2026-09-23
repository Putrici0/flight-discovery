package flightdiscovery.paull.application.recommendation;

import flightdiscovery.paull.domain.model.FlightRoute;

record GeneratedRouteCandidate(
        FlightRoute route,
        double estimatedTimeMinutes,
        RouteDurationBand band
) {
}
