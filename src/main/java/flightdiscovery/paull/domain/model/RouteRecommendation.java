package flightdiscovery.paull.domain.model;

public record RouteRecommendation(
        FlightRoute route,
        RouteScore score,
        String summary
) {
}
