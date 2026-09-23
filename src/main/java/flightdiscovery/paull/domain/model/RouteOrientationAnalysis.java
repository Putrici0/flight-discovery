package flightdiscovery.paull.domain.model;

import java.util.List;

public record RouteOrientationAnalysis(
        double sunAzimuthDegrees,
        double sunExposureScore,
        double visualOrientationScore,
        double orientationScore,
        String predominantSunPosition,
        String recommendedViewingSide,
        String summary,
        String favorableReason,
        List<String> frontalSunLegs,
        List<RouteLegOrientation> legs
) {
}
