package flightdiscovery.paull.application.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import flightdiscovery.paull.domain.model.Airport;
import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.RouteType;
import flightdiscovery.paull.domain.model.Waypoint;

class SightseeingServiceTest {

    private final SightseeingService service = new SightseeingService(new SunExposureService());

    @Test
    void localHighScenicRoutesCanReceiveSightseeingTime() {
        FlightRoute route = scenicRoute(List.of("coast"), 9.0);

        double sightseeingTime = service.sightseeingTimeMinutes(route, 60.0, 120.0);

        assertTrue(sightseeingTime > 0.0);
        assertTrue(sightseeingTime <= 30.0);
    }

    @Test
    void interIslandRoutesDoNotReceiveSightseeingTime() {
        FlightRoute route = scenicRoute(List.of("inter-island"), 9.0);

        double sightseeingTime = service.sightseeingTimeMinutes(route, 60.0, 120.0);

        assertEquals(0.0, sightseeingTime, 0.01);
    }

    @Test
    void sightseeingManeuversAddOrbitPointsToFlightPath() {
        FlightRoute route = scenicRoute(List.of("coast"), 9.0);
        double sightseeingTime = service.sightseeingTimeMinutes(route, 60.0, 120.0);

        var maneuvers = service.sightseeingManeuvers(route, sightseeingTime, 226.0, 180.0);
        var flightPath = service.flightPath(route, new Waypoint("TEST", 0.0, 0.0), maneuvers);

        assertFalse(maneuvers.isEmpty());
        assertFalse(maneuvers.getFirst().orbitPath().isEmpty());
        assertTrue(flightPath.size() > route.waypoints().size() + 2);
    }

    private FlightRoute scenicRoute(List<String> tags, double scenicScore) {
        return new FlightRoute(
                "test",
                "Test",
                "Test",
                RouteType.PREDEFINED,
                new Airport("TEST", "Test", 0.0, 0.0),
                List.of(
                        new Waypoint("Point A", 0.1, 0.0),
                        new Waypoint("Point B", 0.2, 0.1)
                ),
                tags,
                0.0,
                0.0,
                scenicScore
        );
    }
}
