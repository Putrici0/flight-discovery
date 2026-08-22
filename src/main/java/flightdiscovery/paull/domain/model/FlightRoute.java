package flightdiscovery.paull.domain.model;

import java.util.List;

public record FlightRoute(
        String id,
        String name,
        String description,
        RouteType routeType,
        Airport departureAirport,
        List<Waypoint> waypoints,
        List<String> tags,
        double estimatedDistanceKm,
        double estimatedDurationMinutes,
        double scenicScore
) {
}
