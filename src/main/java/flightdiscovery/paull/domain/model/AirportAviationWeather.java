package flightdiscovery.paull.domain.model;

import java.util.List;

public record AirportAviationWeather(
        String airportCode,
        String airportName,
        double latitude,
        double longitude,
        double distanceFromRouteKm,
        MetarReport metar,
        TafReport taf,
        boolean tafAvailable,
        List<String> warnings
) {
}
