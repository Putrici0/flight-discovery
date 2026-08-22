package flightdiscovery.paull.domain.model;

public record WeatherData(
        double windKmh,
        double cloudCoverPercent,
        double precipitationProbability,
        double visibilityKm,
        double weatherScore
) {
}
