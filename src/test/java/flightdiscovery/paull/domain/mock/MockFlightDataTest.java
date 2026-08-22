package flightdiscovery.paull.domain.mock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import flightdiscovery.paull.domain.repository.MockWaypointRepository;

class MockFlightDataTest {

    private final MockWaypointRepository repository = new MockWaypointRepository();

    @Test
    void providesPreparedVisualWaypointCatalogForCanaryIslands() {
        var waypoints = repository.findAll();

        assertTrue(waypoints.size() >= 10);
        assertTrue(waypoints.stream().anyMatch(waypoint -> waypoint.id().startsWith("gc-")));
        assertTrue(waypoints.stream().anyMatch(waypoint -> waypoint.id().startsWith("tf-")));
    }

    @Test
    void visualWaypointsHaveTagsAndScenicValue() {
        var waypoints = repository.findAll();

        assertTrue(waypoints.stream().allMatch(waypoint -> waypoint.id() != null && !waypoint.id().isBlank()));
        assertTrue(waypoints.stream().allMatch(waypoint -> waypoint.scenicValue() >= 0.0));
        assertTrue(waypoints.stream().allMatch(waypoint -> waypoint.scenicValue() <= 10.0));
        assertFalse(waypoints.stream().flatMap(waypoint -> waypoint.tags().stream()).toList().isEmpty());
        assertTrue(waypoints.stream().anyMatch(waypoint -> waypoint.tags().contains("volcano")));
        assertTrue(waypoints.stream().anyMatch(waypoint -> waypoint.tags().contains("coast")));
        assertTrue(waypoints.stream().anyMatch(waypoint -> waypoint.tags().contains("mountain")));
    }
}
