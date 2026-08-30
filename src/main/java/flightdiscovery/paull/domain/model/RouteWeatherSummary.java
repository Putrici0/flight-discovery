package flightdiscovery.paull.domain.model;

public record RouteWeatherSummary(
        double averageWindKmh,
        double maxWindKmh,
        double averageCloudCoverPercent,
        double maxPrecipitationProbability,
        double minVisibilityKm,
        double averageTemperatureCelsius,
        double weatherScore,
        String provider,
        @com.fasterxml.jackson.annotation.JsonProperty("isMock")
        boolean isMock
) {
}
