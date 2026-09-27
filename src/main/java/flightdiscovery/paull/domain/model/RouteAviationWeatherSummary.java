package flightdiscovery.paull.domain.model;

import java.util.List;

public record RouteAviationWeatherSummary(
        String provider,
        String sourceUrl,
        boolean operationalUseAllowed,
        List<AirportAviationWeather> airports,
        List<String> warnings
) {
}
