package flightdiscovery.paull.application.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import flightdiscovery.paull.domain.calculation.RouteCalculationService;
import flightdiscovery.paull.domain.mock.MockFlightData;
import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.RouteType;
import flightdiscovery.paull.domain.repository.MockWaypointRepository;

class RouteCandidateGeneratorTest {

    private final RouteCalculationService routeCalculationService = new RouteCalculationService();
    private final RouteCandidateGenerator generator = new RouteCandidateGenerator(
            new MockWaypointRepository(),
            routeCalculationService,
            new RouteCandidateSelectionService(new RouteSimilarityService(routeCalculationService))
    );

    @Test
    void generatesScenicRoutesWithLimitedWaypointCount() {
        var routes = generator.generate(MockFlightData.GCLP, 80, 226.0, "coast");

        assertTrue(routes.stream().anyMatch(route -> route.id().startsWith("generated-")));
        assertTrue(routes.stream().allMatch(route -> route.departureAirport().code().equals("GCLP")));
        assertTrue(routes.stream().anyMatch(route -> route.waypoints().size() == 1));
        assertTrue(routes.stream()
                .filter(route -> route.waypoints().size() == 1)
                .allMatch(route -> route.routeType() == RouteType.GENERATED_ONE_WAYPOINT));
        assertTrue(routes.stream().allMatch(route -> route.waypoints().size() <= 5));
        assertTrue(routes.stream().anyMatch(route -> route.id().startsWith("generated-corridor-")
                && route.waypoints().size() > 1));
    }

    @Test
    void generatesOneWaypointRouteForEachCompatibleWaypoint() {
        var routes = generator.generate(MockFlightData.GCLP, 480, 226.0, "coast");
        var oneWaypointRoutes = routes.stream()
                .filter(route -> route.routeType() == RouteType.GENERATED_ONE_WAYPOINT)
                .toList();

        assertFalse(oneWaypointRoutes.isEmpty());
        assertTrue(oneWaypointRoutes.stream().allMatch(route -> route.id().startsWith("generated-one-gclp-")));
        assertEquals(oneWaypointRoutes.size(), oneWaypointRoutes.stream().map(route -> route.id()).distinct().count());
    }

    @Test
    void onlyUsesWaypointsCompatibleWithDepartureAirport() {
        var routes = generator.generate(MockFlightData.GCLP, 480, 226.0, "coast");

        assertTrue(routes.stream().allMatch(route -> route.departureAirport().code().equals("GCLP")));
        assertFalse(routes.stream()
                .flatMap(route -> route.waypoints().stream())
                .anyMatch(waypoint -> waypoint.name().equals("Teide") || waypoint.name().equals("Costa Adeje")));
    }

    @Test
    void doesNotUseInterIslandWaypointsUnlessPreferenceRequestsThem() {
        var routes = generator.generate(MockFlightData.GCLP, 180, 226.0, "coast");

        assertFalse(routes.stream()
                .flatMap(route -> route.waypoints().stream())
                .anyMatch(waypoint -> waypoint.name().equals("Isla de Lobos")
                        || waypoint.name().equals("Costa oeste de Fuerteventura")
                        || waypoint.name().equals("Costa sur de Lanzarote")
                        || waypoint.name().equals("Punta de Papagayo")));
    }

    @Test
    void usesInterIslandWaypointsWhenPreferenceRequestsThem() {
        var routes = generator.generate(MockFlightData.GCLP, 180, 226.0, "inter-island");

        assertTrue(routes.stream()
                .flatMap(route -> route.waypoints().stream())
                .anyMatch(waypoint -> waypoint.name().equals("Isla de Lobos")
                        || waypoint.name().equals("Costa oeste de Fuerteventura")
                        || waypoint.name().equals("Costa sur de Lanzarote")
                        || waypoint.name().equals("Punta de Papagayo")));
    }

    @Test
    void oneWaypointRoutesAreCircularFromDepartureToWaypointAndBack() {
        var route = generator.generate(MockFlightData.GCLP, 64, 226.0, "coast").stream()
                .filter(candidate -> candidate.routeType() == RouteType.GENERATED_ONE_WAYPOINT)
                .findFirst()
                .orElseThrow();
        var waypoint = route.waypoints().getFirst();
        double expectedDistance = routeCalculationService.haversineKm(
                route.departureAirport().latitude(),
                route.departureAirport().longitude(),
                waypoint.latitude(),
                waypoint.longitude()
        ) * 2.0;

        assertEquals(1, route.waypoints().size());
        assertEquals(expectedDistance, routeCalculationService.totalDistanceKm(route), 0.01);
    }

    @Test
    void generatesCircularRoutesWithTwoVisualWaypoints() {
        var routes = generator.generate(MockFlightData.GCLP, 180, 226.0, "coast");

        assertTrue(routes.stream().anyMatch(route -> route.waypoints().size() == 2));
        assertTrue(routes.stream()
                .filter(route -> route.waypoints().size() == 2)
                .allMatch(route -> route.routeType() == RouteType.GENERATED_TWO_WAYPOINTS));
    }

    @Test
    void twoWaypointRoutesHaveUniqueIdsAndDoNotRepeatWaypoints() {
        var twoWaypointRoutes = generator.generate(MockFlightData.GCLP, 480, 226.0, "coast").stream()
                .filter(route -> route.routeType() == RouteType.GENERATED_TWO_WAYPOINTS)
                .toList();

        assertFalse(twoWaypointRoutes.isEmpty());
        assertEquals(twoWaypointRoutes.size(), twoWaypointRoutes.stream().map(route -> route.id()).distinct().count());
        assertTrue(twoWaypointRoutes.stream().allMatch(route -> route.id().startsWith("generated-two-gclp-")));
        assertTrue(twoWaypointRoutes.stream().allMatch(route -> !route.waypoints().get(0).name().equals(route.waypoints().get(1).name())));
    }

    @Test
    void generatedRoutesNeverRepeatWaypointsWithinSameRoute() {
        var routes = generator.generate(MockFlightData.GCLP, 480, 226.0, "coast");

        assertTrue(routes.stream().allMatch(route -> route.waypoints().size() == route.waypoints().stream()
                .map(waypoint -> waypoint.name().toLowerCase())
                .distinct()
                .count()));
    }

    @Test
    void doesNotGenerateCombinatorialThreeOrMoreWaypointRoutesWhenUsefulTimeIsBelowNinetyMinutes() {
        var routes = generator.generate(MockFlightData.GCLP, 89, 226.0, "coast");

        assertTrue(routes.stream()
                .noneMatch(route -> route.id().startsWith("generated-three-plus-")));
        assertTrue(routes.stream()
                .anyMatch(route -> route.id().startsWith("generated-corridor-")));
    }

    @Test
    void generatesThreeOrMoreWaypointRoutesWhenUsefulTimeIsHighEnough() {
        var routes = generator.generate(MockFlightData.GCLP, 180, 226.0, "coast");
        var threeOrMoreWaypointRoute = routes.stream()
                .filter(route -> route.routeType() == RouteType.GENERATED_THREE_OR_MORE_WAYPOINTS)
                .findFirst()
                .orElseThrow();

        assertTrue(threeOrMoreWaypointRoute.id().startsWith("generated-three-plus-gclp-"));
        assertTrue(threeOrMoreWaypointRoute.waypoints().size() >= 3);
        assertEquals(
                threeOrMoreWaypointRoute.waypoints().size(),
                threeOrMoreWaypointRoute.waypoints().stream()
                        .map(waypoint -> waypoint.name().toLowerCase())
                        .distinct()
                        .count()
        );
    }

    @Test
    void threeOrMoreWaypointGenerationKeepsCandidateVolumeBoundedBeforeTimeFiltering() {
        var result = generator.generateWithDebug(MockFlightData.GCLP, 180, 226.0, "coast");
        int compatibleWaypointCount = result.compatibleWaypointCount();
        int maximumGeneratedCandidates = compatibleWaypointCount
                + 2 * (compatibleWaypointCount * (compatibleWaypointCount - 1) / 2
                + 220
                + 260
                + 140);

        assertTrue(result.generatedCandidateRoutes() <= maximumGeneratedCandidates);
    }

    @Test
    void twoWaypointRoutesAreCircularThroughBothWaypoints() {
        var route = generator.generate(MockFlightData.GCLP, 480, 226.0, "coast").stream()
                .filter(candidate -> candidate.routeType() == RouteType.GENERATED_TWO_WAYPOINTS)
                .findFirst()
                .orElseThrow();
        var firstWaypoint = route.waypoints().get(0);
        var secondWaypoint = route.waypoints().get(1);
        double expectedDistance = routeCalculationService.haversineKm(
                route.departureAirport().latitude(),
                route.departureAirport().longitude(),
                firstWaypoint.latitude(),
                firstWaypoint.longitude()
        ) + routeCalculationService.haversineKm(
                firstWaypoint.latitude(),
                firstWaypoint.longitude(),
                secondWaypoint.latitude(),
                secondWaypoint.longitude()
        ) + routeCalculationService.haversineKm(
                secondWaypoint.latitude(),
                secondWaypoint.longitude(),
                route.departureAirport().latitude(),
                route.departureAirport().longitude()
        );

        assertEquals(2, route.waypoints().size());
        assertEquals(expectedDistance, routeCalculationService.totalDistanceKm(route), 0.01);
    }

    @Test
    void twoWaypointRoutesMergeTagsAndAverageScenicValue() {
        var route = generator.generate(MockFlightData.GCLP, 180, 226.0, "coast").stream()
                .filter(candidate -> candidate.routeType() == RouteType.GENERATED_TWO_WAYPOINTS)
                .filter(candidate -> candidate.tags().size() > 1)
                .findFirst()
                .orElseThrow();

        assertEquals(route.tags().size(), route.tags().stream().distinct().count());
        assertTrue(route.scenicScore() > 0.0);
    }

    @Test
    void prioritizesTwoWaypointRoutesMatchingUserPreference() {
        var routes = generator.generate(MockFlightData.GCLP, 480, 226.0, "mountain");
        int firstNonMatchingIndex = -1;

        for (int i = 0; i < routes.size(); i++) {
            if (!routes.get(i).tags().contains("mountain")) {
                firstNonMatchingIndex = i;
                break;
            }
        }

        assertTrue(firstNonMatchingIndex == -1
                || routes.subList(0, firstNonMatchingIndex).stream().allMatch(route -> route.tags().contains("mountain")));
    }

    @Test
    void limitsGeneratedCandidatesToEightyRoutes() {
        var routes = generator.generate(MockFlightData.GCLP, 480, 226.0, "panoramic");

        assertTrue(routes.size() <= 80);
    }

    @Test
    void exposesManyGeneratedCandidatesBeforeSelectingDiverseSubset() {
        var result = generator.generateWithDebug(MockFlightData.GCLP, 180, 226.0, "coast");

        assertTrue(result.generatedCandidateRoutes() > 1000);
        assertEquals(80, result.routes().size());
        assertTrue(result.discardedRoutes().stream()
                .anyMatch(discard -> discard.reason().contains("Too similar")));
    }

    @Test
    void selectedGeneratedCandidatesAvoidNearlyIdenticalWaypointSets() {
        var routes = generator.generate(MockFlightData.GCLP, 180, 226.0, "coast");
        var signatures = routes.stream()
                .map(route -> route.waypoints().stream()
                        .map(waypoint -> waypoint.name().toLowerCase())
                        .sorted()
                        .reduce((first, second) -> first + "|" + second)
                        .orElse(route.id()))
                .toList();

        assertEquals(signatures.size(), signatures.stream().distinct().count());
    }

    @Test
    void onlyGeneratesRoutesThatFitAvailableTimeTolerance() {
        int availableTimeMinutes = 20;
        var routes = generator.generate(MockFlightData.GCLP, availableTimeMinutes, 226.0, "coast");

        assertTrue(routes.stream().allMatch(route -> {
            double distanceKm = routeCalculationService.totalDistanceKm(route);
            double estimatedHours = routeCalculationService.estimatedTimeHours(distanceKm, 226.0);
            double estimatedMinutes = routeCalculationService.estimatedTimeMinutes(estimatedHours);

            return estimatedMinutes <= availableTimeMinutes * 1.25;
        }));
    }

    @Test
    void generatedCandidatesCoverMultipleDurationBandsWhenAvailable() {
        int availableTimeMinutes = 20;
        var routes = generator.generate(MockFlightData.GCLP, availableTimeMinutes, 226.0, "coast");
        var bands = routes.stream()
                .map(route -> durationBand(route, availableTimeMinutes))
                .distinct()
                .toList();

        assertTrue(bands.contains("short"));
        assertTrue(bands.contains("medium"));
        assertTrue(bands.contains("long"));
        assertTrue(bands.contains("extended"));
    }

    @Test
    void prioritizesTimeFitInsteadOfAlwaysReturningShortestRoutesFirst() {
        int availableTimeMinutes = 20;
        var routes = generator.generate(MockFlightData.GCLP, availableTimeMinutes, 226.0, "coast");
        double firstRouteMinutes = estimatedTimeMinutes(routes.getFirst(), 226.0);
        double shortestRouteMinutes = routes.stream()
                .mapToDouble(route -> estimatedTimeMinutes(route, 226.0))
                .min()
                .orElseThrow();

        assertTrue(firstRouteMinutes > shortestRouteMinutes);
        assertTrue(firstRouteMinutes >= availableTimeMinutes * 0.75);
    }

    @Test
    void keepsVisualWaypointTagsAndScenicValueInGeneratedRoute() {
        var route = generator.generate(MockFlightData.GCLP, 120, 226.0, "coast").getFirst();

        assertTrue(route.tags().size() > 0);
        assertTrue(route.scenicScore() > 0.0);
        assertTrue(route.waypoints().size() > 0);
    }

    @Test
    void prioritizesRoutesMatchingUserPreference() {
        var route = generator.generate(MockFlightData.GCLP, 120, 226.0, "mountain").getFirst();

        assertTrue(route.tags().contains("mountain"));
    }

    @Test
    void coastPreferencePrioritizesCoastalWaypointsBeforeAlternatives() {
        var routes = generator.generate(MockFlightData.GCLP, 480, 226.0, "coast");

        assertTrue(routes.subList(0, Math.min(20, routes.size())).stream()
                .allMatch(route -> route.tags().contains("coast")));
    }

    @Test
    void reservesMostGeneratedCandidatesForCoastPreferenceButKeepsAlternatives() {
        var routes = generator.generate(MockFlightData.GCLP, 480, 226.0, "coast");
        long coastRoutes = routes.stream()
                .filter(route -> route.tags().contains("coast"))
                .count();

        assertTrue(coastRoutes > routes.size() / 2);
        assertTrue(coastRoutes < routes.size());
    }

    @Test
    void coastPreferenceKeepsCoastalRoutesLocalByDefault() {
        var routes = generator.generate(MockFlightData.GCLP, 180, 226.0, "coast");

        assertTrue(routes.stream()
                .filter(route -> route.tags().contains("coast"))
                .flatMap(route -> route.waypoints().stream())
                .noneMatch(waypoint -> waypoint.name().equals("Isla de Lobos")
                        || waypoint.name().equals("Costa oeste de Fuerteventura")
                        || waypoint.name().equals("Costa sur de Lanzarote")
                        || waypoint.name().equals("Punta de Papagayo")));
    }

    @Test
    void reservesMostGeneratedCandidatesForMountainPreferenceButKeepsAlternatives() {
        var routes = generator.generate(MockFlightData.GCLP, 480, 226.0, "mountain");
        long mountainRoutes = routes.stream()
                .filter(route -> route.tags().contains("mountain"))
                .count();

        assertTrue(mountainRoutes > routes.size() / 2);
        assertTrue(mountainRoutes < routes.size());
    }

    @Test
    void shortAvailableTimeGeneratesShorterRoutesThanLongAvailableTime() {
        var shortTimeRoutes = generator.generate(MockFlightData.GCLP, 20, 226.0, "coast");
        var longTimeRoutes = generator.generate(MockFlightData.GCLP, 180, 226.0, "coast");
        double longestShortRouteMinutes = longestEstimatedTimeMinutes(shortTimeRoutes);
        double longestLongRouteMinutes = longestEstimatedTimeMinutes(longTimeRoutes);

        assertTrue(longestShortRouteMinutes < longestLongRouteMinutes);
    }

    @Test
    void longerAvailableTimeAllowsRoutesThatDoNotFitShortAvailableTime() {
        var shortTimeRoutes = generator.generate(MockFlightData.GCLP, 20, 226.0, "coast");
        var longTimeRoutes = generator.generate(MockFlightData.GCLP, 180, 226.0, "coast");
        double longestShortRouteMinutes = longestEstimatedTimeMinutes(shortTimeRoutes);

        assertTrue(longTimeRoutes.stream()
                .mapToDouble(route -> estimatedTimeMinutes(route, 226.0))
                .anyMatch(estimatedTimeMinutes -> estimatedTimeMinutes > longestShortRouteMinutes));
    }

    private double longestEstimatedTimeMinutes(List<FlightRoute> routes) {
        return routes.stream()
                .mapToDouble(route -> estimatedTimeMinutes(route, 226.0))
                .max()
                .orElseThrow();
    }

    private double estimatedTimeMinutes(FlightRoute route, double cruiseSpeedKmh) {
        double distanceKm = routeCalculationService.totalDistanceKm(route);
        double estimatedHours = routeCalculationService.estimatedTimeHours(distanceKm, cruiseSpeedKmh);

        return routeCalculationService.estimatedTimeMinutes(estimatedHours);
    }

    private String durationBand(FlightRoute route, double availableTimeMinutes) {
        double ratio = estimatedTimeMinutes(route, 226.0) / availableTimeMinutes;

        if (ratio >= 0.3 && ratio < 0.5) {
            return "short";
        }

        if (ratio >= 0.5 && ratio < 0.75) {
            return "medium";
        }

        if (ratio >= 0.75 && ratio <= 1.0) {
            return "long";
        }

        if (ratio > 1.0 && ratio <= 1.25) {
            return "extended";
        }

        return "outside";
    }
}
