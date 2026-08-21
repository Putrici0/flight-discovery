package flightdiscovery.paull.domain.model;

import java.util.List;

public record RouteRecommendationRequest(
        Airport departureAirport,
        Aircraft aircraft,
        int availableTimeMinutes,
        double availableFuelLiters,
        String weatherSummary,
        List<String> preferences
) {
}
