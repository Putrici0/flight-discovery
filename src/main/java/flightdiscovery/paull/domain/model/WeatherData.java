package flightdiscovery.paull.domain.model;

public record WeatherData(
        double windKmh,
        double cloudCoverPercent,
        double precipitationProbability,
        double visibilityKm,
        double temperatureCelsius,
        double weatherScore,
        String provider,
        @com.fasterxml.jackson.annotation.JsonProperty("isMock")
        boolean isMock
) {
    public WeatherData(
            double windKmh,
            double cloudCoverPercent,
            double precipitationProbability,
            double visibilityKm,
            double temperatureCelsius,
            double weatherScore
    ) {
        this(
                windKmh,
                cloudCoverPercent,
                precipitationProbability,
                visibilityKm,
                temperatureCelsius,
                weatherScore,
                "mock",
                true
        );
    }
}
