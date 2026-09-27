package flightdiscovery.paull.domain.model;

import java.util.List;

public record TafReport(
        String airportCode,
        String stationName,
        String rawText,
        String issuedAt,
        String bulletinAt,
        String validFrom,
        String validTo,
        Long ageMinutes,
        List<TafForecastPeriod> forecastPeriods,
        String source
) {
}
