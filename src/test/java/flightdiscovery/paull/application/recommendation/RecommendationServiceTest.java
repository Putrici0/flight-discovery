package flightdiscovery.paull.application.recommendation;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import flightdiscovery.paull.api.recommendation.RecommendationRequest;
import flightdiscovery.paull.api.recommendation.RecommendedRouteResponse;
import flightdiscovery.paull.api.recommendation.RouteDurationCategory;
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

    private static final int CESSNA_RECOMMENDED_RESERVE_MINUTES = 45;

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
    void recommendsShortRoutesWhenThirtyUsefulMinutesAreAvailable() {
        double usefulTimeMinutes = 30.0;
        var recommendations = recommendationService.recommend(requestWithUsefulTime((int) usefulTimeMinutes)).recommendations();

        assertFalse(recommendations.isEmpty());
        assertTrue(recommendations.stream()
                .allMatch(recommendation -> recommendation.estimatedTimeMinutes() <= usefulTimeMinutes * 1.25));
    }

    @Test
    void prioritizesRoutesNearNinetyToOneHundredTenMinutesWhenTwoHoursAreAvailable() {
        var recommendations = recommendationService.recommend(requestWithUsefulTime(120)).recommendations();
        var topThreeRecommendations = recommendations.subList(0, Math.min(3, recommendations.size()));
        long nearTargetRecommendations = topThreeRecommendations.stream()
                .filter(recommendation -> recommendation.estimatedTimeMinutes() >= 90.0)
                .filter(recommendation -> recommendation.estimatedTimeMinutes() <= 110.0)
                .count();

        assertFalse(topThreeRecommendations.isEmpty());
        assertTrue(nearTargetRecommendations >= Math.min(2, topThreeRecommendations.size()));
    }

    @Test
    void recommendationsForThreeHoursIncludeLongerRoutesThanRecommendationsForOneHour() {
        var oneHourRecommendations = recommendationService.recommend(requestWithUsefulTime(60)).recommendations();
        var threeHourRecommendations = recommendationService.recommend(requestWithUsefulTime(180)).recommendations();
        double longestOneHourRecommendation = oneHourRecommendations.stream()
                .mapToDouble(RecommendedRouteResponse::estimatedTimeMinutes)
                .max()
                .orElseThrow();

        assertTrue(threeHourRecommendations.stream()
                .anyMatch(recommendation -> recommendation.estimatedTimeMinutes() > longestOneHourRecommendation));
    }

    @Test
    void prioritizesGoodFitRecommendationsWhenAvailable() {
        var request = requestWithAvailableTime(120);
        var response = recommendationService.recommend(request);
        var debug = recommendationService.debug(request);
        var recommendations = response.recommendations();
        long availableGoodFitCandidates = debug.candidates().stream()
                .filter(candidate -> candidate.totalScore() != null)
                .filter(candidate -> durationCategory(candidate.estimatedTimeMinutes(), debug.usefulAvailableTimeMinutes())
                        == RouteDurationCategory.GOOD_FIT)
                .count();
        long goodFitRecommendations = recommendations.stream()
                .filter(recommendation -> recommendation.routeDurationCategory() == RouteDurationCategory.GOOD_FIT)
                .count();

        if (availableGoodFitCandidates > 0) {
            assertEquals(RouteDurationCategory.GOOD_FIT, recommendations.getFirst().routeDurationCategory());
            assertTrue(goodFitRecommendations >= Math.min(2, availableGoodFitCandidates));
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
    void shortPreferenceCanChangeRecommendationDurationCategories() {
        var coastCategories = recommendationService.recommend(requestWithPreference("coast")).recommendations().stream()
                .map(RecommendedRouteResponse::routeDurationCategory)
                .toList();
        var shortCategories = recommendationService.recommend(requestWithPreference("short")).recommendations().stream()
                .map(RecommendedRouteResponse::routeDurationCategory)
                .toList();

        assertNotEquals(coastCategories, shortCategories);
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
    void avoidsTooShortRoutesWhenPreferenceIsNotShortAndAlternativesExist() {
        var recommendations = recommendationService.recommend(requestWithSafetyMargin(120, 15)).recommendations();

        assertTrue(recommendations.stream()
                .noneMatch(recommendation -> recommendation.routeDurationCategory() == RouteDurationCategory.TOO_SHORT));
    }

    @Test
    void allowsTooShortRoutesWhenPreferenceIsShort() {
        var recommendations = recommendationService.recommend(requestWithPreference("short")).recommendations();

        assertTrue(recommendations.stream()
                .anyMatch(recommendation -> recommendation.routeDurationCategory() == RouteDurationCategory.TOO_SHORT));
    }

    @Test
    void shortPreferenceDoesNotPenalizeShortRoutes() {
        var tooShortRecommendations = recommendationService.recommend(requestWithPreferenceAndUsefulTime("short", 120))
                .recommendations()
                .stream()
                .filter(recommendation -> recommendation.routeDurationCategory() == RouteDurationCategory.TOO_SHORT)
                .toList();

        assertFalse(tooShortRecommendations.isEmpty());
        assertTrue(tooShortRecommendations.stream()
                .allMatch(recommendation -> recommendation.scoreBreakdown().timeFitScore() == 85.0));
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
    void keepsMaximumFiveRecommendationsAfterDurationSelection() {
        var recommendations = recommendationService.recommend(requestWithAvailableTime(120)).recommendations();

        assertTrue(recommendations.size() <= 5);
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
    void includesThreeOrMoreWaypointGeneratedRoutesWhenUsefulTimeIsHighEnough() {
        var recommendations = recommendationService.recommend(requestWithAvailableTime(180)).recommendations();

        assertTrue(recommendations.stream()
                .anyMatch(recommendation -> recommendation.routeType() == RouteType.GENERATED_THREE_OR_MORE_WAYPOINTS
                        && recommendation.waypoints().size() >= 3));
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
    void finalSelectionDoesNotReturnOnlyVeryShortRoutesWhenAlternativesExist() {
        var categories = recommendationService.recommend(requestWithAvailableTime(120))
                .recommendations()
                .stream()
                .map(RecommendedRouteResponse::routeDurationCategory)
                .distinct()
                .toList();

        assertFalse(categories.size() == 1 && categories.contains(RouteDurationCategory.TOO_SHORT));
    }

    @Test
    void tooShortRoutesDoNotDominateTopFiveWhenAlternativesExist() {
        var request = requestWithUsefulTime(120);
        var debug = recommendationService.debug(request);
        boolean hasNonTooShortAlternatives = debug.candidates().stream()
                .filter(candidate -> candidate.totalScore() != null)
                .anyMatch(candidate -> durationCategory(candidate.estimatedTimeMinutes(), debug.usefulAvailableTimeMinutes())
                        != RouteDurationCategory.TOO_SHORT);
        var topFiveRecommendations = recommendationService.recommend(request)
                .recommendations()
                .stream()
                .limit(5)
                .toList();
        long tooShortRecommendations = topFiveRecommendations.stream()
                .filter(recommendation -> recommendation.routeDurationCategory() == RouteDurationCategory.TOO_SHORT)
                .count();

        if (hasNonTooShortAlternatives) {
            assertTrue(tooShortRecommendations <= topFiveRecommendations.size() / 2);
        }
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

    private RecommendationRequest requestWithUsefulTime(int usefulAvailableTimeMinutes) {
        return requestWithAvailableTime(usefulAvailableTimeMinutes + CESSNA_RECOMMENDED_RESERVE_MINUTES);
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

    private RecommendationRequest requestWithPreferenceAndUsefulTime(String preference, int usefulAvailableTimeMinutes) {
        return new RecommendationRequest(
                "GCLP",
                usefulAvailableTimeMinutes + CESSNA_RECOMMENDED_RESERVE_MINUTES,
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

    private RouteDurationCategory durationCategory(double estimatedTimeMinutes, double usefulAvailableTimeMinutes) {
        double usageRatio = estimatedTimeMinutes / usefulAvailableTimeMinutes;

        if (usageRatio < 0.4) {
            return RouteDurationCategory.TOO_SHORT;
        }

        if (usageRatio < 0.7) {
            return RouteDurationCategory.SHORT;
        }

        if (usageRatio < 0.9) {
            return RouteDurationCategory.GOOD_FIT;
        }

        if (usageRatio <= 1.0) {
            return RouteDurationCategory.LONG;
        }

        if (usageRatio <= 1.25) {
            return RouteDurationCategory.SLIGHTLY_OVER_TIME;
        }

        return RouteDurationCategory.TOO_LONG;
    }
}
