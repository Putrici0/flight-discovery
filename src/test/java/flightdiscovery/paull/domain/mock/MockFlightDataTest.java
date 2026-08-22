package flightdiscovery.paull.domain.mock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import flightdiscovery.paull.domain.repository.MockWaypointRepository;

class MockFlightDataTest {

    private final MockWaypointRepository repository = new MockWaypointRepository();

    @Test
    void providesPreparedVisualWaypointCatalogForCanaryIslands() {
        var waypoints = repository.findAll();

        assertTrue(waypoints.size() >= 30);
        assertTrue(waypoints.stream().anyMatch(waypoint -> waypoint.id().startsWith("gc-")));
        assertTrue(waypoints.stream().anyMatch(waypoint -> waypoint.id().startsWith("tf-")));
    }

    @Test
    void visualWaypointsHaveTagsAndScenicValue() {
        var waypoints = repository.findAll();

        assertTrue(waypoints.stream().allMatch(waypoint -> waypoint.id() != null && !waypoint.id().isBlank()));
        assertTrue(waypoints.stream().allMatch(waypoint -> waypoint.scenicValue() >= 0.0));
        assertTrue(waypoints.stream().allMatch(waypoint -> waypoint.scenicValue() <= 100.0));
        assertTrue(waypoints.stream().allMatch(waypoint -> !waypoint.tags().isEmpty()));
        assertFalse(waypoints.stream().flatMap(waypoint -> waypoint.tags().stream()).toList().isEmpty());
        assertTrue(waypoints.stream().anyMatch(waypoint -> waypoint.tags().contains("volcano")));
        assertTrue(waypoints.stream().anyMatch(waypoint -> waypoint.tags().contains("coast")));
        assertTrue(waypoints.stream().anyMatch(waypoint -> waypoint.tags().contains("mountain")));
    }

    @Test
    void includesRequestedGranCanariaAndTenerifeWaypoints() {
        var waypointNames = repository.findAll().stream()
                .map(waypoint -> waypoint.name().toLowerCase())
                .toList();

        List.of(
                "dunas de maspalomas",
                "puerto de mogan",
                "agaete",
                "roque nublo",
                "tejeda",
                "las canteras",
                "arucas",
                "barranco de guayadeque",
                "teide",
                "acantilados de los gigantes",
                "macizo de anaga",
                "la orotava",
                "garachico",
                "costa adeje",
                "la laguna",
                "punta de teno"
        ).forEach(expectedName -> assertTrue(waypointNames.contains(expectedName)));
    }
}
