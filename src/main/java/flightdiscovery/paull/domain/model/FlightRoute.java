package flightdiscovery.paull.domain.model;

import java.util.List;

public record FlightRoute(
        String id,
        String name,
        String description,
        Airport departureAirport,
        List<Waypoint> waypoints,
        double estimatedDistanceKm,
        double estimatedDurationMinutes,
        double scenicScore
) {
}
