package flightdiscovery.paull.application.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import flightdiscovery.paull.domain.model.Airport;
import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.RouteType;
import flightdiscovery.paull.domain.model.Waypoint;

class SunExposureServiceTest {

    private final SunExposureService service = new SunExposureService();

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
}
