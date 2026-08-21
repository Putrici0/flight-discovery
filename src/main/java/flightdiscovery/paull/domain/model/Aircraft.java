package flightdiscovery.paull.domain.model;

public record Aircraft(
        String id,
        String name,
        double cruiseSpeedKmh,
        double fuelBurnLitersPerHour,
        double usableFuelLiters
) {
}
