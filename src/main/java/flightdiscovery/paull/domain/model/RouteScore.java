package flightdiscovery.paull.domain.model;

public record RouteScore(
        double weatherScore,
        double timeFitScore,
        double scenicScore,
        double costScore,
        double totalScore
) {
}
