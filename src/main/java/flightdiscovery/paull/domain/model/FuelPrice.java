package flightdiscovery.paull.domain.model;

public record FuelPrice(
        String fuelType,
        double pricePerLiter,
        FuelPriceSource source
) {
}
