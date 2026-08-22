package flightdiscovery.paull.application.recommendation;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import flightdiscovery.paull.api.recommendation.RecommendationRequest;
import flightdiscovery.paull.api.recommendation.RecommendedRouteResponse;
import flightdiscovery.paull.domain.calculation.RouteCalculationService;
import flightdiscovery.paull.domain.model.RouteType;
import flightdiscovery.paull.domain.repository.MockAircraftRepository;
import flightdiscovery.paull.domain.repository.MockAirportRepository;
import flightdiscovery.paull.domain.repository.MockFuelPriceRepository;
import flightdiscovery.paull.domain.repository.MockRouteRepository;
import flightdiscovery.paull.domain.repository.MockWaypointRepository;
import flightdiscovery.paull.domain.scoring.RouteScoringService;
import flightdiscovery.paull.domain.weather.MockWeatherService;

class RecommendationServiceTest {

    private final RecommendationService recommendationService = new RecommendationService(
            new MockRouteRepository(),
            new MockAirportRepository(),
            new MockAircraftRepository(),
            new MockFuelPriceRepository(),
            new RouteCalculationService(),
            new RouteScoringService(),
            new RouteCandidateGenerator(new MockWaypointRepository(), new RouteCalculationService()),
            new MockWeatherService()
    );

    @Test
    void recommendsShorterRoutesWhenAvailableTimeIsShorter() {
        var shortTimeRecommendations = recommendationService.recommend(requestWithAvailableTime(65)).recommendations();
        var longTimeRecommendations = recommendationService.recommend(requestWithAvailableTime(225)).recommendations();
        double longestShortRecommendation = shortTimeRecommendations.stream()
                .mapToDouble(RecommendedRouteResponse::estimatedTimeMinutes)
                .max()
                .orElseThrow();

        assertTrue(longTimeRecommendations.stream()
                .anyMatch(recommendation -> recommendation.estimatedTimeMinutes() > longestShortRecommendation));
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
        int availableTimeMinutes = 65;
        int recommendedReserveMinutes = 45;
        double usefulFlightTimeMinutes = availableTimeMinutes - recommendedReserveMinutes;
        var response = recommendationService.recommend(requestWithAvailableTime(availableTimeMinutes));

        assertTrue(response.recommendations().stream()
                .allMatch(recommendation -> recommendation.estimatedTimeMinutes() <= usefulFlightTimeMinutes * 1.25));
    }

    @Test
    void doesNotReturnRoutesThatExceedUsefulTimeTolerance() {
        int availableTimeMinutes = 55;
        int safetyMarginPercent = 15;
        double usefulFlightTimeMinutes = 8.5;
        var response = recommendationService.recommend(requestWithSafetyMargin(availableTimeMinutes, safetyMarginPercent));

        assertTrue(response.recommendations().stream()
                .allMatch(recommendation -> recommendation.estimatedTimeMinutes() <= usefulFlightTimeMinutes * 1.25));
    }

    @Test
    void allowsRoutesUpToTwentyFivePercentOverUsefulTimeWithWarning() {
        double usefulFlightTimeMinutes = 8.5;
        var recommendations = recommendationService.recommend(requestWithSafetyMargin(55, 15))
                .recommendations()
                .stream()
                .filter(recommendation -> recommendation.estimatedTimeMinutes() > usefulFlightTimeMinutes)
                .toList();

        assertTrue(recommendations.size() > 0);
        assertTrue(recommendations.stream()
                .allMatch(recommendation -> recommendation.estimatedTimeMinutes() <= usefulFlightTimeMinutes * 1.25));
        assertTrue(recommendations.stream()
                .allMatch(recommendation -> recommendation.warnings().contains("Esta ruta supera ligeramente el tiempo disponible")));
    }

    @Test
    void defaultsSafetyMarginToFifteenPercentWhenOmitted() {
        var defaultMarginRecommendations = recommendationService.recommend(requestWithSafetyMargin(120, null)).recommendations();
        var explicitMarginRecommendations = recommendationService.recommend(requestWithSafetyMargin(120, 15)).recommendations();

        assertEquals(
                explicitMarginRecommendations.stream().map(RecommendedRouteResponse::id).toList(),
                defaultMarginRecommendations.stream().map(RecommendedRouteResponse::id).toList()
        );
    }

    @Test
    void returnsWarningWhenFewerThanThreeRoutesAreAvailableAfterFiltering() {
        var response = recommendationService.recommend(requestWithAvailableTime(1));

        assertTrue(response.recommendations().size() < 3);
        assertTrue(response.warnings().contains("Fewer than 3 candidate routes fit within the available flight time plus 25% tolerance."));
    }

    @Test
    void userPreferenceInfluencesRecommendationRanking() {
        var coastRecommendations = recommendationService.recommend(requestWithPreference("coast")).recommendations();
        var mountainRecommendations = recommendationService.recommend(requestWithPreference("mountain")).recommendations();

        assertNotEquals(
                coastRecommendations.stream().map(RecommendedRouteResponse::id).toList(),
                mountainRecommendations.stream().map(RecommendedRouteResponse::id).toList()
        );
    }

    @Test
    void explanationMentionsWhetherRouteMatchesPreference() {
        var recommendation = recommendationService.recommend(requestWithPreference("mountain"))
                .recommendations()
                .getFirst();

        assertTrue(recommendation.explanation().contains("preferencia mountain"));
    }

    @Test
    void explanationMentionsHowRouteUsesAvailableTime() {
        var recommendation = recommendationService.recommend(requestWithAvailableTime(120))
                .recommendations()
                .getFirst();

        assertTrue(recommendation.explanation().contains("aprovecha "));
        assertTrue(recommendation.explanation().contains("el tiempo disponible"));
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
    void usesAircraftDefaultsWhenAircraftIdIsProvided() {
        var cessnaDefaults = recommendationService.recommend(requestWithAircraftDefaults("cessna-172")).recommendations();
        var explicitCessnaValues = recommendationService.recommend(requestWithAircraftValues("cessna-172", 226.0, 34.0)).recommendations();
        var routePair = firstCommonRoutePair(cessnaDefaults, explicitCessnaValues);

        assertEquals(routePair.second().estimatedTimeMinutes(), routePair.first().estimatedTimeMinutes(), 0.01);
        assertEquals(routePair.second().estimatedFuelLiters(), routePair.first().estimatedFuelLiters(), 0.01);
    }

    @Test
    void manualCruiseSpeedOverridesAircraftCruiseSpeed() {
        var aircraftDefaultRoutes = recommendationService.recommend(requestWithAircraftDefaults("diamond-da40")).recommendations();
        var manualSpeedRoutes = recommendationService.recommend(requestWithAircraftValues("diamond-da40", 226.0, null)).recommendations();
        var routePair = firstCommonRoutePair(aircraftDefaultRoutes, manualSpeedRoutes);

        assertTrue(routePair.second().estimatedTimeMinutes() > routePair.first().estimatedTimeMinutes());
    }

    @Test
    void manualFuelBurnOverridesAircraftFuelBurn() {
        var aircraftDefaultRoutes = recommendationService.recommend(requestWithAircraftDefaults("diamond-da40")).recommendations();
        var manualFuelBurnRoutes = recommendationService.recommend(requestWithAircraftValues("diamond-da40", null, 40.0)).recommendations();
        var routePair = firstCommonRoutePair(aircraftDefaultRoutes, manualFuelBurnRoutes);

        assertTrue(routePair.second().estimatedFuelLiters() > routePair.first().estimatedFuelLiters());
    }

    @Test
    void routeWarningsAlwaysMentionSimulatedWeather() {
        var recommendation = recommendationService.recommend(requestWithAvailableTime(120))
                .recommendations()
                .getFirst();

        assertTrue(recommendation.warnings().contains("La meteorologia todavia es simulada"));
    }

    @Test
    void recommendationsIncludeMockWeatherData() {
        var recommendation = recommendationService.recommend(requestWithAvailableTime(120))
                .recommendations()
                .getFirst();

        assertScoreInRange(recommendation.weatherScore());
        assertEquals(recommendation.scoreBreakdown().weatherScore(), recommendation.weatherScore(), 0.01);
        assertTrue(recommendation.windKmh() >= 0.0);
        assertTrue(recommendation.cloudCoverPercent() >= 0.0);
        assertTrue(recommendation.cloudCoverPercent() <= 100.0);
        assertTrue(recommendation.precipitationProbability() >= 0.0);
        assertTrue(recommendation.precipitationProbability() <= 100.0);
        assertTrue(recommendation.visibilityKm() >= 0.0);
        assertTrue(recommendation.explanation().contains("meteorologia simulada"));
    }

    @Test
    void responseIncludesRecommendationDebugInfo() {
        var response = recommendationService.recommend(requestWithAvailableTime(120));

        assertTrue(response.debugInfo().generatedCandidateRoutes() > 0);
        assertTrue(response.debugInfo().discardedByTimeRoutes() >= 0);
        assertEquals(response.recommendations().size(), response.debugInfo().recommendedRoutes());
    }

    @Test
    void routeWarningsMentionLowTimeMargin() {
        var recommendations = recommendationService.recommend(requestWithAvailableTime(62))
                .recommendations()
                .stream()
                .filter(recommendation -> recommendation.warnings().contains("Esta ruta deja poco margen de tiempo"))
                .toList();

        assertTrue(recommendations.size() > 0);
    }

    @Test
    void routeWarningsMentionSlightTimeOverrun() {
        var recommendations = recommendationService.recommend(requestWithSafetyMargin(55, 15))
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
    void usesMockFuelPriceWhenManualPriceIsOmitted() {
        var recommendation = recommendationService.recommend(requestWithoutFuelPrice())
                .recommendations()
                .getFirst();

        assertEquals(2.85, recommendation.fuelPricePerLiter(), 0.01);
        assertEquals("MOCK", recommendation.fuelPriceSource());
    }

    @Test
    void usesMockFuelPriceFromAircraftFuelTypeWhenManualPriceIsOmitted() {
        var recommendation = recommendationService.recommend(requestWithoutFuelPrice("diamond-da40"))
                .recommendations()
                .getFirst();

        assertEquals(1.95, recommendation.fuelPricePerLiter(), 0.01);
        assertEquals("MOCK", recommendation.fuelPriceSource());
    }

    @Test
    void usesManualFuelPriceWhenProvided() {
        var recommendation = recommendationService.recommend(requestWithFuelPrice(3.2))
                .recommendations()
                .getFirst();

        assertEquals(3.2, recommendation.fuelPricePerLiter(), 0.01);
        assertEquals("MANUAL", recommendation.fuelPriceSource());
    }

    @Test
    void manualFuelPriceOverridesMockFuelPrice() {
        var recommendation = recommendationService.recommend(requestWithFuelPrice("diamond-da40", 3.2))
                .recommendations()
                .getFirst();

        assertEquals(3.2, recommendation.fuelPricePerLiter(), 0.01);
        assertEquals("MANUAL", recommendation.fuelPriceSource());
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

    @Test
    void doesNotReturnRoutesWithExactlySameWaypoints() {
        var recommendations = recommendationService.recommend(requestWithAvailableTime(120)).recommendations();
        var waypointSignatures = recommendations.stream()
                .map(recommendation -> recommendation.waypoints().stream()
                        .map(waypoint -> waypoint.name().toLowerCase())
                        .sorted()
                        .reduce((first, second) -> first + "|" + second)
                        .orElse(recommendation.id()))
                .toList();

        assertEquals(waypointSignatures.size(), waypointSignatures.stream().distinct().count());
    }

    @Test
    void mixesRouteTypesWhenGoodAlternativesExist() {
        var routeTypes = recommendationService.recommend(requestWithAvailableTime(120))
                .recommendations()
                .stream()
                .map(RecommendedRouteResponse::routeType)
                .distinct()
                .toList();

        assertTrue(routeTypes.contains(RouteType.PREDEFINED));
        assertTrue(routeTypes.contains(RouteType.GENERATED_ONE_WAYPOINT));
        assertTrue(routeTypes.contains(RouteType.GENERATED_TWO_WAYPOINTS));
    }

    @Test
    void avoidsReturningOnlyOnePrimaryTagWhenAlternativesExist() {
        var recommendations = recommendationService.recommend(requestWithAvailableTime(120)).recommendations();

        assertFalse(recommendations.stream().allMatch(recommendation -> recommendation.id().contains("coast")));
    }

    private RecommendationRequest requestWithAvailableTime(int availableTimeMinutes) {
        return new RecommendationRequest(
                "GCLP",
                availableTimeMinutes,
                "cessna-172",
                226.0,
                34.0,
                2.3,
                "coast",
                0
        );
    }

    private RecommendationRequest requestWithSafetyMargin(int availableTimeMinutes, Integer safetyMarginPercent) {
        return new RecommendationRequest(
                "GCLP",
                availableTimeMinutes,
                "cessna-172",
                226.0,
                34.0,
                2.3,
                "coast",
                safetyMarginPercent
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
                preference,
                0
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
                "coast",
                0
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
                "coast",
                0
        );
    }

    private RecommendationRequest requestWithoutFuelPrice() {
        return requestWithoutFuelPrice("cessna-172");
    }

    private RecommendationRequest requestWithoutFuelPrice(String aircraftId) {
        return new RecommendationRequest(
                "GCLP",
                120,
                aircraftId,
                null,
                null,
                null,
                "coast",
                0
        );
    }

    private RecommendationRequest requestWithFuelPrice(String aircraftId, double fuelPricePerLiter) {
        return new RecommendationRequest(
                "GCLP",
                120,
                aircraftId,
                null,
                null,
                fuelPricePerLiter,
                "coast",
                0
        );
    }

    private RecommendationRequest requestWithAircraftValues(String aircraftId, Double cruiseSpeedKmh, Double fuelBurnLitersPerHour) {
        return new RecommendationRequest(
                "GCLP",
                120,
                aircraftId,
                cruiseSpeedKmh,
                fuelBurnLitersPerHour,
                2.3,
                "coast",
                0
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
                "coast",
                0
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

    private void assertScoreInRange(double score) {
        assertTrue(score >= 0.0);
        assertTrue(score <= 100.0);
    }
}
