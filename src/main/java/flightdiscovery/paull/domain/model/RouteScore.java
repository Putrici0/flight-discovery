package flightdiscovery.paull.domain.model;

public record RouteScore(
        double weatherScore,
        double timeFitScore,
        double preferenceScore,
        double scenicScore,
        double visualOrientationScore,
        double costScore,
        double totalScore
) {
    public RouteScore(
            double weatherScore,
            double timeFitScore,
            double preferenceScore,
            double scenicScore,
            double costScore,
            double totalScore
    ) {
        this(weatherScore, timeFitScore, preferenceScore, scenicScore, 70.0, costScore, totalScore);
    }
}
