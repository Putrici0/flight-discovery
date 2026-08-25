package flightdiscovery.paull.domain.model;

import java.time.LocalDateTime;
import java.util.List;

public record RouteRecommendationRequest(
        Airport departureAirport,
        Aircraft aircraft,
        int availableTimeMinutes,
        double availableFuelLiters,
        String weatherSummary,
        List<String> preferences,
        LocalDateTime plannedDepartureDateTime
) {
    public RouteRecommendationRequest(
            Airport departureAirport,
            Aircraft aircraft,
            int availableTimeMinutes,
            double availableFuelLiters,
            String weatherSummary,
            List<String> preferences
    ) {
        this(
                departureAirport,
                aircraft,
                availableTimeMinutes,
                availableFuelLiters,
                weatherSummary,
                preferences,
                LocalDateTime.now().withSecond(0).withNano(0)
        );
    }
}
