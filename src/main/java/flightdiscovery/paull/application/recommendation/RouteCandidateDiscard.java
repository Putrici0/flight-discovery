package flightdiscovery.paull.application.recommendation;

import flightdiscovery.paull.domain.model.FlightRoute;

public record RouteCandidateDiscard(
        FlightRoute route,
        String reason,
        double estimatedTimeMinutes,
        double limitMinutes
) {
}
