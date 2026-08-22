package flightdiscovery.paull.domain.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import flightdiscovery.paull.domain.model.Airport;
import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.Waypoint;

class RouteCalculationServiceTest {

    private final RouteCalculationService calculator = new RouteCalculationService();

    @Test
    void calculatesHaversineDistanceBetweenTwoCoordinates() {
        double distance = calculator.haversineKm(0.0, 0.0, 0.0, 1.0);

        assertEquals(111.19, distance, 0.1);
    }

    @Test
    void calculatesTotalDistanceFromDepartureThroughWaypointsAndBackToDeparture() {
        Airport departure = new Airport("TEST", "Test Airport", 0.0, 0.0);
        FlightRoute route = new FlightRoute(
                "test-route",
                "Test route",
                "Test route",
                departure,
                List.of(
                        new Waypoint("Waypoint 1", 0.0, 1.0),
                        new Waypoint("Waypoint 2", 1.0, 1.0)
                ),
                0.0,
                0.0,
                5.0
        );

        double distance = calculator.totalDistanceKm(route);

        assertEquals(379.64, distance, 0.2);
    }

    @Test
    void calculatesEstimatedTimeFromDistanceAndCruiseSpeed() {
        double estimatedTime = calculator.estimatedTimeMinutes(220.0, 220.0);

        assertEquals(60.0, estimatedTime, 0.01);
    }

    @Test
    void calculatesEstimatedFuelFromTimeAndFuelBurn() {
        double estimatedFuel = calculator.estimatedFuelLiters(90.0, 40.0);

        assertEquals(60.0, estimatedFuel, 0.01);
    }

    @Test
    void calculatesEstimatedCostFromFuelAndPrice() {
        double estimatedCost = calculator.estimatedCost(50.0, 2.3);

        assertEquals(115.0, estimatedCost, 0.01);
    }
}
