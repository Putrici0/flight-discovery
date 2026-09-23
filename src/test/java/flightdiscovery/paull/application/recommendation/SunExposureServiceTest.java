package flightdiscovery.paull.application.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import flightdiscovery.paull.domain.model.Airport;
import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.RouteOrientationAnalysis;
import flightdiscovery.paull.domain.model.RouteType;
import flightdiscovery.paull.domain.model.Waypoint;
import flightdiscovery.paull.domain.repository.MockWaypointRepository;

class SunExposureServiceTest {

    private final SunExposureService service = new SunExposureService(new MockWaypointRepository());

    @Test
    void sunAzimuthKeepsExistingDaylightApproximation() {
        assertEquals(0.0, service.sunAzimuthDegrees("2026-08-25T06:30"), 0.01);
        assertEquals(90.0, service.sunAzimuthDegrees("2026-08-25T07:00"), 0.01);
        assertEquals(180.0, service.sunAzimuthDegrees("2026-08-25T13:30"), 0.01);
        assertEquals(270.0, service.sunAzimuthDegrees("2026-08-25T20:00"), 0.01);
    }

    @Test
    void nightTimeExposureKeepsPenalty() {
        FlightRoute route = new FlightRoute(
                "test",
                "Test",
                "Test",
                RouteType.PREDEFINED,
                new Airport("TEST", "Test", 0.0, 0.0),
                List.of(new Waypoint("North", 1.0, 0.0)),
                List.of("coast"),
                0.0,
                0.0,
                8.0
        );

        double score = service.sunExposureScore(route, new Waypoint("TEST", 0.0, 0.0), 0.0, "2026-08-25T06:30");

        assertEquals(15.0, score, 0.01);
        assertTrue(service.sunExposureSummary(score, 0.0).contains("luz solar baja"));
    }

    @Test
    void orientationAnalysisDescribesEachLeg() {
        FlightRoute route = new FlightRoute(
                "east-west",
                "East west",
                "East west",
                RouteType.PREDEFINED,
                new Airport("TEST", "Test", 0.0, 0.0),
                List.of(new Waypoint("East", 0.0, 1.0), new Waypoint("North", 1.0, 1.0)),
                List.of("coast"),
                0.0,
                0.0,
                90.0
        );

        RouteOrientationAnalysis analysis = service.orientationAnalysis(
                route,
                new Waypoint("TEST", 0.0, 0.0),
                service.sunAzimuthDegrees("2026-08-25T07:00"),
                "2026-08-25T07:00"
        );

        assertEquals(3, analysis.legs().size());
        assertEquals("FRONT", analysis.legs().getFirst().sunPosition());
        assertTrue(analysis.legs().getFirst().frontalSunPenalty() > 80.0);
        assertTrue(analysis.frontalSunLegs().contains("TEST -> East"));
    }

    @Test
    void routeAndReverseCanChangePreferenceWhenSolarPositionChanges() {
        FlightRoute outboundEast = new FlightRoute(
                "east-route",
                "East route",
                "East route",
                RouteType.PREDEFINED,
                new Airport("TEST", "Test", 0.0, 0.0),
                List.of(
                        new Waypoint("East", 0.0, 1.0),
                        new Waypoint("East north", 0.1, 1.0)
                ),
                List.of("coast"),
                0.0,
                0.0,
                90.0
        );
        FlightRoute reverse = service.reversedRouteVariant(outboundEast);

        RouteOrientationAnalysis morningForward = service.orientationAnalysis(
                outboundEast,
                new Waypoint("TEST", 0.0, 0.0),
                service.sunAzimuthDegrees("2026-08-25T07:00"),
                "2026-08-25T07:00"
        );
        RouteOrientationAnalysis morningReverse = service.orientationAnalysis(
                reverse,
                new Waypoint("TEST", 0.0, 0.0),
                service.sunAzimuthDegrees("2026-08-25T07:00"),
                "2026-08-25T07:00"
        );
        RouteOrientationAnalysis eveningForward = service.orientationAnalysis(
                outboundEast,
                new Waypoint("TEST", 0.0, 0.0),
                service.sunAzimuthDegrees("2026-08-25T20:00"),
                "2026-08-25T20:00"
        );
        RouteOrientationAnalysis eveningReverse = service.orientationAnalysis(
                reverse,
                new Waypoint("TEST", 0.0, 0.0),
                service.sunAzimuthDegrees("2026-08-25T20:00"),
                "2026-08-25T20:00"
        );

        assertTrue(morningReverse.orientationScore() > morningForward.orientationScore());
        assertTrue(eveningForward.orientationScore() > eveningReverse.orientationScore());
    }
}
