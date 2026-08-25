package flightdiscovery.paull.api.recommendation;

public record SightseeingManeuverResponse(
        String waypointName,
        String maneuverType,
        double minutes,
        double radiusKm,
        String instruction
) {
}
