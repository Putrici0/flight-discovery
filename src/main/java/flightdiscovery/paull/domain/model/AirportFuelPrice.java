package flightdiscovery.paull.domain.model;

import java.time.LocalDate;

public record AirportFuelPrice(
        String airportCode,
        String fuelType,
        double pricePerLiter,
        String currency,
        String source,
        LocalDate lastUpdated,
        @com.fasterxml.jackson.annotation.JsonProperty("isMock")
        boolean isMock
) {
}
