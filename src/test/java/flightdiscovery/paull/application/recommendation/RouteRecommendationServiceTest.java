package flightdiscovery.paull.application.recommendation;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import flightdiscovery.paull.api.recommendation.RecommendationRequest;
import flightdiscovery.paull.api.recommendation.RecommendedRouteResponse;
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

    @Test
    void aircraftDefaultsChangeEstimatedTimeAndFuelForSameRoute() {
        var cessnaRoute = routeById(recommendationService.recommend(requestWithAircraftDefaults("cessna-172"))
                .recommendations(), "gclp-coastal-south");
        var diamondRoute = routeById(recommendationService.recommend(requestWithAircraftDefaults("diamond-da40"))
                .recommendations(), "gclp-coastal-south");

        assertTrue(diamondRoute.estimatedTimeMinutes() < cessnaRoute.estimatedTimeMinutes());
        assertTrue(diamondRoute.estimatedFuelLiters() < cessnaRoute.estimatedFuelLiters());
    }

    @Test
    void manualSpeedAndFuelBurnOverrideAircraftDefaults() {
        var aircraftDefaultsRoute = routeById(recommendationService.recommend(requestWithAircraftDefaults("diamond-da40"))
                .recommendations(), "gclp-coastal-south");
        var manualOverrideRoute = routeById(recommendationService.recommend(requestWithManualAircraftValues())
                .recommendations(), "gclp-coastal-south");

        assertNotEquals(aircraftDefaultsRoute.estimatedTimeMinutes(), manualOverrideRoute.estimatedTimeMinutes());
        assertNotEquals(aircraftDefaultsRoute.estimatedFuelLiters(), manualOverrideRoute.estimatedFuelLiters());
        assertEquals(60.93, manualOverrideRoute.estimatedTimeMinutes(), 0.01);
        assertEquals(40.6, manualOverrideRoute.estimatedFuelLiters(), 0.01);
    }

    @Test
    void routeWarningsAlwaysMentionSimulatedWeather() {
        var recommendation = recommendationService.recommend(requestWithAvailableTime(120))
                .recommendations()
                .getFirst();

        assertTrue(recommendation.warnings().contains("La meteorologia todavia es simulada"));
    }

    @Test
    void routeWarningsMentionLowTimeMargin() {
        var recommendation = routeById(recommendationService.recommend(requestWithAvailableTime(23))
                .recommendations(), "gclp-coastal-south");

        assertTrue(recommendation.warnings().contains("Esta ruta deja poco margen de tiempo"));
    }

    @Test
    void routeWarningsMentionSlightTimeOverrun() {
        var recommendation = routeById(recommendationService.recommend(requestWithAvailableTime(19))
                .recommendations(), "gclp-coastal-south");

        assertTrue(recommendation.warnings().contains("Esta ruta supera ligeramente el tiempo disponible"));
    }

    @Test
    void routeWarningsMentionHighEstimatedCost() {
        var recommendation = recommendationService.recommend(requestWithFuelPrice(20.0))
                .recommendations()
                .getFirst();

        assertTrue(recommendation.warnings().contains("El coste estimado es alto"));
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

    private RecommendationRequest requestWithAircraftDefaults(String aircraftId) {
        return new RecommendationRequest(
                "GCLP",
                120,
                aircraftId,
                null,
                null,
                2.3,
                "coast"
        );
    }

    private RecommendationRequest requestWithFuelPrice(double fuelPricePerLiter) {
        return new RecommendationRequest(
                "GCLP",
                120,
                "cessna-172",
                226.0,
                34.0,
                fuelPricePerLiter,
                "coast"
        );
    }

    private RecommendationRequest requestWithManualAircraftValues() {
        return new RecommendationRequest(
                "GCLP",
                120,
                "diamond-da40",
                78.0,
                40.0,
                2.3,
                "coast"
        );
    }

    private RecommendedRouteResponse routeById(List<RecommendedRouteResponse> recommendations, String routeId) {
        return recommendations.stream()
                .filter(recommendation -> recommendation.id().equals(routeId))
                .findFirst()
                .orElseThrow();
    }
}
