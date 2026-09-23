package flightdiscovery.paull.domain.model;

public record RouteLegOrientation(
        String fromName,
        String toName,
        double distanceKm,
        double aircraftBearingDegrees,
        double sunAzimuthDegrees,
        double relativeSunAngleDegrees,
        String sunPosition,
        double frontalSunPenalty,
        String recommendedViewingSide,
        double viewingQualityScore,
        String explanation
) {
}
