package flightdiscovery.paull.domain.model;

import java.util.List;

public record VisualWaypoint(
        String id,
        String name,
        double latitude,
        double longitude,
        List<String> compatibleDepartureAirportCodes,
        List<String> tags,
        double scenicValue
) {
}
