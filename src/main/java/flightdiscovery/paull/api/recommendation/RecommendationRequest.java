package flightdiscovery.paull.api.recommendation;

public record RecommendationRequest(
        String departureAirport,
        int availableFlightTimeMinutes,
        String aircraftId,
        double cruiseSpeedKmh,
        double fuelBurnLitersPerHour,
        double fuelPricePerLiter,
        String preference
) {
}
