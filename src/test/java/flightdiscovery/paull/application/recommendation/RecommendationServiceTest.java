package flightdiscovery.paull.application.recommendation;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
    private final RouteCalculationService routeCalculationService = new RouteCalculationService();
    private final RecommendationTimeService recommendationTimeService = new RecommendationTimeService();
    private final SunExposureService sunExposureService = new SunExposureService();
    private final RouteSimilarityService routeSimilarityService = new RouteSimilarityService(routeCalculationService);

    private final RecommendationService recommendationService = new RecommendationService(
            new MockRouteRepository(),
            new MockAirportRepository(),
            new MockAircraftRepository(),
            new MockFuelPriceRepository(),
            routeCalculationService,
            new RouteScoringService(),
            new RouteCandidateGenerator(
                    new MockWaypointRepository(),
                    routeCalculationService,
                    new RouteCandidateSelectionService(routeSimilarityService)
            ),
            recommendationTimeService,
            new RecommendationSelectionService(routeSimilarityService),
            new SightseeingService(sunExposureService),
            sunExposureService,
            new RouteWeatherSummaryService(),
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
    void doesNotForceRoutesNearTargetDurationWhenLocalRoutesAreBetter() {
        var recommendations = recommendationService.recommend(requestWithUsefulTime(120)).recommendations();
        var topThreeRecommendations = recommendations.subList(0, Math.min(3, recommendations.size()));

        assertFalse(topThreeRecommendations.isEmpty());
        assertTrue(topThreeRecommendations.stream().noneMatch(this::isInterIslandRecommendation));
        assertTrue(topThreeRecommendations.stream()
                .anyMatch(recommendation -> recommendation.estimatedTimeMinutes() < 90.0));
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
    void highAvailableTimeCanReturnShortLocalRoutesInsteadOfArtificialLongRoutes() {
        var request = requestWithSafetyMargin(150, 15);
        var debug = recommendationService.debug(request);

        assertFalse(debug.recommendations().isEmpty());
        assertTrue(debug.recommendations().stream()
                .noneMatch(this::isInterIslandRecommendation));
    }

    @Test
    void recommendationsCanChangeAsAvailableTimeIncreasesWithoutForcingLongerAverageDuration() {
        var mediumRecommendations = recommendationService.recommend(requestWithSafetyMargin(150, 15)).recommendations();
        var longRecommendations = recommendationService.recommend(requestWithSafetyMargin(240, 15)).recommendations();

        assertNotEquals(
                mediumRecommendations.stream().map(RecommendedRouteResponse::id).toList(),
                longRecommendations.stream().map(RecommendedRouteResponse::id).toList()
        );
    }

    @Test
    void highAvailableTimeDoesNotForceInterIslandRoutesAboveLocalAlternatives() {
        var debug = recommendationService.debug(requestWithSafetyMargin(420, 15));
        var topThreeRecommendations = debug.recommendations().subList(0, Math.min(3, debug.recommendations().size()));

        assertFalse(topThreeRecommendations.isEmpty());
        assertTrue(topThreeRecommendations.stream().noneMatch(this::isInterIslandRecommendation));
    }

    @Test
    void highAvailableTimeWithMountainPreferenceStillPrioritizesLocalRoutes() {
        var debug = recommendationService.debug(requestWithPreferenceAvailableTimeAndSafetyMargin("mountain", 420, 15));
        var topThreeRecommendations = debug.recommendations().subList(0, Math.min(3, debug.recommendations().size()));

        assertFalse(topThreeRecommendations.isEmpty());
        assertTrue(topThreeRecommendations.stream().noneMatch(this::isInterIslandRecommendation));
    }

    @Test
    void localScenicRoutesCanUseExplicitSightseeingTimeInsteadOfArtificialDistance() {
        var recommendations = recommendationService.recommend(requestWithPreferenceAndUsefulTime("mountain", 120)).recommendations();
        var scenicLocalRecommendation = recommendations.stream()
                .filter(recommendation -> !isInterIslandRecommendation(recommendation))
                .filter(recommendation -> recommendation.sightseeingTimeMinutes() > 0.0)
                .findFirst()
                .orElseThrow();

        assertTrue(scenicLocalRecommendation.estimatedTimeMinutes() > scenicLocalRecommendation.baseFlightTimeMinutes());
        assertTrue(scenicLocalRecommendation.sightseeingTimeMinutes() <= 30.0);
        assertTrue(scenicLocalRecommendation.flightPath().size() > scenicLocalRecommendation.waypoints().size() + 2);
        assertFalse(scenicLocalRecommendation.sightseeingManeuvers().isEmpty());
        assertTrue(scenicLocalRecommendation.sightseeingManeuvers().getFirst().instruction().contains("orbita visual"));
        assertFalse(scenicLocalRecommendation.sightseeingManeuvers().getFirst().orbitPath().isEmpty());
        assertTrue(scenicLocalRecommendation.explanation().contains("observacion escenica local"));
    }

    @Test
    void plannedDepartureDateTimeChangesSunExposureScore() {
        var morningRecommendations = recommendationService.recommend(requestWithPlannedDepartureDateTime("2026-08-25T08:00"))
                .recommendations();
        var afternoonRecommendations = recommendationService.recommend(requestWithPlannedDepartureDateTime("2026-08-25T18:00"))
                .recommendations();
        var routePair = firstCommonRoutePair(morningRecommendations, afternoonRecommendations);
        var morningRecommendation = routePair.first();
        var afternoonRecommendation = routePair.second();

        assertEquals("2026-08-25T08:00", morningRecommendation.plannedDepartureDateTime());
        assertEquals("2026-08-25T18:00", afternoonRecommendation.plannedDepartureDateTime());
        assertNotEquals(morningRecommendation.sunExposureScore(), afternoonRecommendation.sunExposureScore());
    }

    @Test
    void plannedDepartureDateTimeCanChangeTopRecommendation() {
        var morningRecommendation = recommendationService.recommend(requestWithPlannedDepartureDateTime("2026-08-25T08:00"))
                .recommendations()
                .getFirst();
        var afternoonRecommendation = recommendationService.recommend(requestWithPlannedDepartureDateTime("2026-08-25T18:00"))
                .recommendations()
                .getFirst();

        assertNotEquals(morningRecommendation.id(), afternoonRecommendation.id());
    }

    @Test
    void defaultsPlannedDepartureDateTimeWhenMissing() {
        var recommendation = recommendationService.recommend(requestWithAvailableTime(120))
                .recommendations()
                .getFirst();

        assertNotNull(recommendation.plannedDepartureDateTime());
    }

    @Test
    void interIslandPreferenceCanPromoteInterIslandRoutes() {
        var recommendations = recommendationService.recommend(requestWithPreferenceAndUsefulTime("inter-island", 180)).recommendations();

        assertTrue(recommendations.stream().anyMatch(this::isInterIslandRecommendation));
    }

    @Test
    void finalSelectionAvoidsRepeatingTheSameWaypointTooOftenWhenAlternativesExist() {
        var recommendations = recommendationService.recommend(requestWithSafetyMargin(180, 15)).recommendations();
        var waypointNames = recommendations.stream()
                .flatMap(recommendation -> recommendation.waypoints().stream())
                .map(waypoint -> waypoint.name().toLowerCase())
                .toList();
        long highestWaypointUsage = waypointNames.stream()
                .mapToLong(waypoint -> java.util.Collections.frequency(waypointNames, waypoint))
                .max()
                .orElse(0);

        assertTrue(highestWaypointUsage <= 4);
    }

    @Test
    void prioritizesGoodFitRecommendationsWhenAvailable() {
        var request = requestWithAvailableTime(120);
        var response = recommendationService.recommend(request);
        var debug = recommendationService.debug(request);
        var recommendations = response.recommendations();
        long availableLocalGoodFitCandidates = debug.candidates().stream()
                .filter(candidate -> candidate.totalScore() != null)
                .filter(candidate -> !isInterIslandCandidate(candidate.routeId(), candidate.routeName()))
                .filter(candidate -> durationCategory(candidate.estimatedTimeMinutes(), debug.usefulAvailableTimeMinutes())
                        == RouteDurationCategory.GOOD_FIT)
                .count();
        long goodFitRecommendations = recommendations.stream()
                .filter(recommendation -> recommendation.routeDurationCategory() == RouteDurationCategory.GOOD_FIT)
                .count();

        if (availableLocalGoodFitCandidates > 0) {
            assertEquals(RouteDurationCategory.GOOD_FIT, recommendations.getFirst().routeDurationCategory());
            assertTrue(goodFitRecommendations >= Math.min(2, availableLocalGoodFitCandidates));
        } else {
            assertFalse(isInterIslandRecommendation(recommendations.getFirst()));
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

        assertTrue(recommendation.warnings().contains("La meteorologia usada es simulada/mock"));
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
        assertNotNull(recommendation.routeWeatherSummary());
        assertEquals("mock", recommendation.weatherProvider());
        assertTrue(recommendation.weatherIsMock());
        assertEquals("mock", recommendation.routeWeatherSummary().provider());
        assertTrue(recommendation.routeWeatherSummary().isMock());
        assertScoreInRange(recommendation.routeWeatherSummary().weatherScore());
        assertTrue(recommendation.routeWeatherSummary().averageWindKmh() >= 0.0);
        assertTrue(recommendation.routeWeatherSummary().maxWindKmh() >= recommendation.routeWeatherSummary().averageWindKmh());
        assertTrue(recommendation.routeWeatherSummary().averageCloudCoverPercent() >= 0.0);
        assertTrue(recommendation.routeWeatherSummary().averageCloudCoverPercent() <= 100.0);
        assertTrue(recommendation.routeWeatherSummary().maxPrecipitationProbability() >= 0.0);
        assertTrue(recommendation.routeWeatherSummary().maxPrecipitationProbability() <= 100.0);
        assertTrue(recommendation.routeWeatherSummary().minVisibilityKm() >= 0.0);
        assertTrue(recommendation.routeWeatherSummary().averageTemperatureCelsius() >= -50.0);
        assertTrue(recommendation.routeWeatherSummary().averageTemperatureCelsius() <= 60.0);
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
        assertEquals("AVGAS_100LL", recommendation.fuelTypeUsed());
        assertEquals("GCLP", recommendation.fuelPriceAirportCode());
        assertTrue(recommendation.fuelPriceIsMock());
    }

    @Test
    void usesMockFuelPriceFromAircraftFuelTypeWhenManualPriceIsOmitted() {
        var recommendation = recommendationService.recommend(requestWithoutFuelPrice("diamond-da40"))
                .recommendations()
                .getFirst();

        assertEquals(1.95, recommendation.fuelPricePerLiter(), 0.01);
        assertEquals("MOCK", recommendation.fuelPriceSource());
        assertEquals("JET_A1", recommendation.fuelTypeUsed());
        assertEquals("GCLP", recommendation.fuelPriceAirportCode());
        assertTrue(recommendation.fuelPriceIsMock());
    }

    @Test
    void usesManualFuelPriceWhenProvided() {
        var recommendation = recommendationService.recommend(requestWithFuelPrice(3.2))
                .recommendations()
                .getFirst();

        assertEquals(3.2, recommendation.fuelPricePerLiter(), 0.01);
        assertEquals("MANUAL", recommendation.fuelPriceSource());
        assertEquals("AVGAS_100LL", recommendation.fuelTypeUsed());
        assertEquals("GCLP", recommendation.fuelPriceAirportCode());
        assertFalse(recommendation.fuelPriceIsMock());
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
    void estimatedCostChangesWhenFuelPriceChanges() {
        var cheaperRecommendation = recommendationService.recommend(requestWithFuelPrice(1.0))
                .recommendations()
                .getFirst();
        var expensiveRecommendation = recommendationService.recommend(requestWithFuelPrice(5.0))
                .recommendations()
                .stream()
                .filter(recommendation -> recommendation.id().equals(cheaperRecommendation.id()))
                .findFirst()
                .orElseThrow();

        assertTrue(expensiveRecommendation.estimatedCost() > cheaperRecommendation.estimatedCost());
    }

    @Test
    void includesGeneratedVisualRoutesAlongsideExistingRouteCatalog() {
        var recommendations = recommendationService.recommend(requestWithAvailableTime(120)).recommendations();

        assertTrue(recommendations.stream().anyMatch(recommendation -> recommendation.id().startsWith("generated-")));
        assertTrue(recommendations.stream()
                .filter(recommendation -> recommendation.id().startsWith("generated-"))
                .allMatch(recommendation -> recommendation.routeType() == RouteType.GENERATED_ONE_WAYPOINT
                        || recommendation.routeType() == RouteType.GENERATED_TWO_WAYPOINTS));
    }

    @Test
    void generatedVisualRoutesUseOneOrTwoVisualWaypoints() {
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
    void debugCandidatesIncludeThreeOrMoreWaypointGeneratedRoutesWhenUsefulTimeIsHighEnough() {
        var debug = recommendationService.debug(requestWithAvailableTime(180));

        assertTrue(debug.candidates().stream()
                .anyMatch(candidate -> candidate.routeType() == RouteType.GENERATED_THREE_OR_MORE_WAYPOINTS));
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
    void tooShortLocalRoutesCanBeatLongerInterIslandAlternativesByDefault() {
        var request = requestWithUsefulTime(120);
        var topFiveRecommendations = recommendationService.recommend(request)
                .recommendations()
                .stream()
                .limit(5)
                .toList();

        assertFalse(topFiveRecommendations.isEmpty());
        assertTrue(topFiveRecommendations.stream().noneMatch(this::isInterIslandRecommendation));
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

    private RecommendationRequest requestWithPreferenceAvailableTimeAndSafetyMargin(
            String preference,
            int availableTimeMinutes,
            int safetyMarginPercent
    ) {
        return new RecommendationRequest(
                "GCLP",
                availableTimeMinutes,
                "cessna-172",
                226.0,
                34.0,
                2.3,
                preference,
                safetyMarginPercent
        );
    }

    private RecommendationRequest requestWithPlannedDepartureDateTime(String plannedDepartureDateTime) {
        return new RecommendationRequest(
                "GCLP",
                120,
                "cessna-172",
                226.0,
                34.0,
                2.3,
                "coast",
                0,
                plannedDepartureDateTime
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

    private boolean isInterIslandRecommendation(RecommendedRouteResponse recommendation) {
        String normalizedRouteText = (recommendation.id() + " " + recommendation.name() + " " + recommendation.explanation()).toLowerCase();

        return isInterIslandText(normalizedRouteText);
    }

    private boolean isInterIslandCandidate(String routeId, String routeName) {
        return isInterIslandText((routeId + " " + routeName).toLowerCase());
    }

    private boolean isInterIslandText(String normalizedRouteText) {
        return normalizedRouteText.contains("inter-island")
                || normalizedRouteText.contains("entre islas")
                || normalizedRouteText.contains("fuerteventura")
                || normalizedRouteText.contains("lanzarote")
                || normalizedRouteText.contains("lobos")
                || normalizedRouteText.contains("papagayo")
                || normalizedRouteText.contains("canal oriental");
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
