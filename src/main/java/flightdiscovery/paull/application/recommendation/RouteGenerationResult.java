package flightdiscovery.paull.application.recommendation;

import java.util.List;

import flightdiscovery.paull.domain.model.FlightRoute;

public record RouteGenerationResult(
        List<FlightRoute> routes,
        int generatedCandidateRoutes,
        int discardedByTimeRoutes
) {
}
