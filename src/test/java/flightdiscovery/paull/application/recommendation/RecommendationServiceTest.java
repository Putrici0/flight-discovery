package flightdiscovery.paull.application.recommendation;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import flightdiscovery.paull.api.recommendation.RecommendationRequest;
import flightdiscovery.paull.api.recommendation.RecommendedRouteResponse;
import flightdiscovery.paull.domain.calculation.RouteCalculationService;
import flightdiscovery.paull.domain.model.RouteType;
import flightdiscovery.paull.domain.repository.MockAircraftRepository;
import flightdiscovery.paull.domain.repository.MockAirportRepository;
import flightdiscovery.paull.domain.repository.MockRouteRepository;
import flightdiscovery.paull.domain.repository.MockWaypointRepository;
import flightdiscovery.paull.domain.scoring.RouteScoringService;

class RecommendationServiceTest {

    private final RecommendationService recommendationService = new RecommendationService(
            new MockRouteRepository(),
            new MockAirportRepository(),
            new MockAircraftRepository(),
            new RouteCalculationService(),
            new RouteScoringService(),
            new RouteCandidateGenerator(new MockWaypointRepository(), new RouteCalculationService())
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
        var response = recommendationService.recommend(requestWithAvailableTime(5));

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
        var routePair = firstCommonRoutePair(
                recommendationService.recommend(requestWithAircraftDefaults("cessna-172")).recommendations(),
                recommendationService.recommend(requestWithAircraftDefaults("diamond-da40")).recommendations()
        );

        assertTrue(routePair.second().estimatedTimeMinutes() < routePair.first().estimatedTimeMinutes());
        assertTrue(routePair.second().estimatedFuelLiters() < routePair.first().estimatedFuelLiters());
    }

    @Test
    void manualSpeedAndFuelBurnOverrideAircraftDefaults() {
        var routePair = firstCommonRoutePair(
                recommendationService.recommend(requestWithAircraftDefaults("diamond-da40")).recommendations(),
                recommendationService.recommend(requestWithManualAircraftValues()).recommendations()
        );

        assertNotEquals(routePair.first().estimatedTimeMinutes(), routePair.second().estimatedTimeMinutes());
        assertNotEquals(routePair.first().estimatedFuelLiters(), routePair.second().estimatedFuelLiters());
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
        var recommendations = recommendationService.recommend(requestWithAvailableTime(17))
                .recommendations()
                .stream()
                .filter(recommendation -> recommendation.warnings().contains("Esta ruta deja poco margen de tiempo"))
                .toList();

        assertTrue(recommendations.size() > 0);
    }

    @Test
    void routeWarningsMentionSlightTimeOverrun() {
        var recommendations = recommendationService.recommend(requestWithAvailableTime(10))
                .recommendations()
                .stream()
                .filter(recommendation -> recommendation.warnings().contains("Esta ruta supera ligeramente el tiempo disponible"))
                .toList();

        assertTrue(recommendations.size() > 0);
    }

    @Test
    void routeWarningsMentionHighEstimatedCost() {
        var recommendation = recommendationService.recommend(requestWithFuelPrice(20.0))
                .recommendations()
                .getFirst();

        assertTrue(recommendation.warnings().contains("El coste estimado es alto"));
    }

    @Test
    void includesGeneratedCircularRoutesAlongsideExistingRouteCatalog() {
        var recommendations = recommendationService.recommend(requestWithAvailableTime(120)).recommendations();

        assertTrue(recommendations.stream().anyMatch(recommendation -> recommendation.id().startsWith("generated-")));
        assertTrue(recommendations.stream()
                .filter(recommendation -> recommendation.id().startsWith("generated-"))
                .allMatch(recommendation -> recommendation.routeType() == RouteType.GENERATED_ONE_WAYPOINT
                        || recommendation.routeType() == RouteType.GENERATED_TWO_WAYPOINTS));
    }

    @Test
    void generatedCircularRoutesUseOneOrTwoVisualWaypoints() {
        var generatedRoute = recommendationService.recommend(requestWithAvailableTime(120))
                .recommendations()
                .stream()
                .filter(recommendation -> recommendation.id().startsWith("generated-"))
                .findFirst()
                .orElseThrow();

        assertTrue(generatedRoute.waypoints().size() >= 1);
        assertTrue(generatedRoute.waypoints().size() <= 2);
        assertEquals(
                generatedRoute.waypoints().size() == 1
                        ? RouteType.GENERATED_ONE_WAYPOINT
                        : RouteType.GENERATED_TWO_WAYPOINTS,
                generatedRoute.routeType()
        );
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
                226.0,
                40.0,
                2.3,
                "coast"
        );
    }

    private RoutePair firstCommonRoutePair(
            List<RecommendedRouteResponse> firstRecommendations,
            List<RecommendedRouteResponse> secondRecommendations
    ) {
        return firstRecommendations.stream()
                .flatMap(first -> secondRecommendations.stream()
                        .filter(second -> second.id().equals(first.id()))
                        .map(second -> new RoutePair(first, second)))
                .findFirst()
                .orElseThrow();
    }

    private record RoutePair(
            RecommendedRouteResponse first,
            RecommendedRouteResponse second
    ) {
    }
}
