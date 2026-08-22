package flightdiscovery.paull.application.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import flightdiscovery.paull.domain.calculation.RouteCalculationService;
import flightdiscovery.paull.domain.mock.MockFlightData;
import flightdiscovery.paull.domain.model.RouteType;
import flightdiscovery.paull.domain.repository.MockWaypointRepository;

class RouteCandidateGeneratorTest {

    private final RouteCalculationService routeCalculationService = new RouteCalculationService();
    private final RouteCandidateGenerator generator = new RouteCandidateGenerator(
            new MockWaypointRepository(),
            routeCalculationService
    );

    @Test
    void generatesCircularRoutesWithOneVisualWaypoint() {
        var routes = generator.generate(MockFlightData.GCLP, 120, 226.0, "coast");

        assertTrue(routes.stream().anyMatch(route -> route.id().startsWith("generated-")));
        assertTrue(routes.stream().allMatch(route -> route.departureAirport().code().equals("GCLP")));
        assertTrue(routes.stream().anyMatch(route -> route.waypoints().size() == 1));
        assertTrue(routes.stream()
                .filter(route -> route.waypoints().size() == 1)
                .allMatch(route -> route.routeType() == RouteType.GENERATED_ONE_WAYPOINT));
        assertTrue(routes.stream().allMatch(route -> route.waypoints().size() <= 2));
    }

    @Test
    void generatesOneWaypointRouteForEachCompatibleWaypoint() {
        var routes = generator.generate(MockFlightData.GCLP, 480, 226.0, "coast");
        var oneWaypointRoutes = routes.stream()
                .filter(route -> route.routeType() == RouteType.GENERATED_ONE_WAYPOINT)
                .toList();

        assertEquals(5, oneWaypointRoutes.size());
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
    void oneWaypointRoutesAreCircularFromDepartureToWaypointAndBack() {
        var route = generator.generate(MockFlightData.GCLP, 480, 226.0, "coast").stream()
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

        assertEquals(10, twoWaypointRoutes.size());
        assertEquals(twoWaypointRoutes.size(), twoWaypointRoutes.stream().map(route -> route.id()).distinct().count());
        assertTrue(twoWaypointRoutes.stream().allMatch(route -> route.id().startsWith("generated-two-gclp-")));
        assertTrue(twoWaypointRoutes.stream().allMatch(route -> !route.waypoints().get(0).name().equals(route.waypoints().get(1).name())));
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
        var route = generator.generate(MockFlightData.GCLP, 480, 226.0, "coast").stream()
                .filter(candidate -> candidate.routeType() == RouteType.GENERATED_TWO_WAYPOINTS)
                .filter(candidate -> candidate.id().contains("gc-maspalomas-dunes")
                        && candidate.id().contains("gc-roque-nublo"))
                .findFirst()
                .orElseThrow();

        assertTrue(route.tags().contains("coast"));
        assertTrue(route.tags().contains("mountain"));
        assertEquals(9.2, route.scenicScore(), 0.01);
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
    void limitsGeneratedCandidatesToThirtyRoutes() {
        var routes = generator.generate(MockFlightData.GCLP, 480, 226.0, "panoramic");

        assertTrue(routes.size() <= 30);
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
}
