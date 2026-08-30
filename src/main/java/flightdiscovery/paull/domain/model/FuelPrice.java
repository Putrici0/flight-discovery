package flightdiscovery.paull.domain.model;

public record FuelPrice(
        String airportCode,
        String fuelType,
        double pricePerLiter,
        String currency,
        FuelPriceSource source,
        boolean isMock
) {
    public FuelPrice(String fuelType, double pricePerLiter, FuelPriceSource source) {
        this(null, fuelType, pricePerLiter, "EUR", source, source == FuelPriceSource.MOCK);
    }
}
