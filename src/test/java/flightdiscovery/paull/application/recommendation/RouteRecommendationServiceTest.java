package flightdiscovery.paull.application.recommendation;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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

    @Test
    void filtersRoutesThatExceedAvailableTimeByMoreThanTwentyFivePercent() {
        int availableTimeMinutes = 20;
        var response = recommendationService.recommend(requestWithAvailableTime(availableTimeMinutes));

        assertTrue(response.recommendations().stream()
                .allMatch(recommendation -> recommendation.estimatedTimeMinutes() <= availableTimeMinutes * 1.25));
    }

    @Test
    void returnsWarningWhenFewerThanThreeRoutesAreAvailableAfterFiltering() {
        var response = recommendationService.recommend(requestWithAvailableTime(20));

        assertTrue(response.recommendations().size() < 3);
        assertTrue(response.warnings().contains("Fewer than 3 routes fit within the available flight time plus 25% tolerance."));
    }

    @Test
    void userPreferenceInfluencesRecommendationRanking() {
        var coastRecommendations = recommendationService.recommend(requestWithPreference("coast")).recommendations();
        var mountainRecommendations = recommendationService.recommend(requestWithPreference("mountain")).recommendations();

        assertNotEquals(coastRecommendations.getFirst().id(), mountainRecommendations.getFirst().id());
    }

    @Test
    void explanationMentionsWhetherRouteMatchesPreference() {
        var recommendation = recommendationService.recommend(requestWithPreference("mountain"))
                .recommendations()
                .getFirst();

        assertTrue(recommendation.explanation().contains("preferencia mountain"));
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

    private RecommendationRequest requestWithPreference(String preference) {
        return new RecommendationRequest(
                "GCLP",
                120,
                "cessna-172",
                226.0,
                34.0,
                2.3,
                preference
        );
    }
}
