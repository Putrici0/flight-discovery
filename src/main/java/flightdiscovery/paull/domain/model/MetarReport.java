package flightdiscovery.paull.domain.model;

import java.util.List;

public record MetarReport(
        String airportCode,
        String stationName,
        String rawText,
        String observedAt,
        String receivedAt,
        Long ageMinutes,
        String flightCategory,
        Object windDirectionDegrees,
        Integer windSpeedKt,
        Integer windGustKt,
        String visibilityStatuteMiles,
        Integer altimeterHpa,
        Integer temperatureCelsius,
        Integer dewpointCelsius,
        String weather,
        List<AviationCloudLayer> clouds,
        String source
) {
}
