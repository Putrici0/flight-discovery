package flightdiscovery.paull.application.recommendation;

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
    void generatesCircularRoutesWithTwoVisualWaypoints() {
        var routes = generator.generate(MockFlightData.GCLP, 180, 226.0, "coast");

        assertTrue(routes.stream().anyMatch(route -> route.waypoints().size() == 2));
        assertTrue(routes.stream()
                .filter(route -> route.waypoints().size() == 2)
                .allMatch(route -> route.routeType() == RouteType.GENERATED_TWO_WAYPOINTS));
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
