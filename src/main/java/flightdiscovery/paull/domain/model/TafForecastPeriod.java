package flightdiscovery.paull.domain.model;

import java.util.List;

public record TafForecastPeriod(
        String from,
        String to,
        String becomingAt,
        String change,
        Integer probability,
        Object windDirectionDegrees,
        Integer windSpeedKt,
        Integer windGustKt,
        String visibilityStatuteMiles,
        String weather,
        List<AviationCloudLayer> clouds
) {
}
