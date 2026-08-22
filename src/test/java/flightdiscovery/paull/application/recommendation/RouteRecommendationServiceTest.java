package flightdiscovery.paull.application.recommendation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import flightdiscovery.paull.api.recommendation.RecommendationRequest;
import flightdiscovery.paull.domain.calculation.RouteCalculationService;
import flightdiscovery.paull.domain.repository.MockFlightDataRepository;
import flightdiscovery.paull.domain.scoring.RouteScoringService;

class RouteRecommendationServiceTest {

    private final RouteRecommendationService recommendationService = new RouteRecommendationService(
            new MockFlightDataRepository(),
            new RouteCalculationService(),
            new RouteScoringService()
    );

    @Test
    void recommendsShorterRoutesWhenAvailableTimeIsShorter() {
        var shortTimeRecommendations = recommendationService.recommend(requestWithAvailableTime(60)).recommendations();
        var longTimeRecommendations = recommendationService.recommend(requestWithAvailableTime(180)).recommendations();

        assertTrue(shortTimeRecommendations.getFirst().estimatedTimeMinutes()
                < longTimeRecommendations.getFirst().estimatedTimeMinutes());
    }

    @Test
    void returnsRecommendationsOrderedByTotalScore() {
        var recommendations = recommendationService.recommend(requestWithAvailableTime(120)).recommendations();

        for (int i = 1; i < recommendations.size(); i++) {
            assertTrue(recommendations.get(i - 1).totalScore() >= recommendations.get(i).totalScore());
        }
    }

    private RecommendationRequest requestWithAvailableTime(int availableTimeMinutes) {
        return new RecommendationRequest(
                "GCLP",
                availableTimeMinutes,
                "cessna-172",
                226.0,
                34.0,
                2.3,
                "coast"
        );
    }
}
