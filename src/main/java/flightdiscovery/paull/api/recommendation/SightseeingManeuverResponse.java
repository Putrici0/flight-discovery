package flightdiscovery.paull.api.recommendation;

import java.util.List;

import flightdiscovery.paull.domain.model.Waypoint;

public record SightseeingManeuverResponse(
        String waypointName,
        String maneuverType,
        double minutes,
        double radiusKm,
        double sunAzimuthDegrees,
        double preferredViewingBearingDegrees,
        List<Waypoint> orbitPath,
        String instruction
) {
}
