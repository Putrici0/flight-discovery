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

    @Test
    void providesAircraftDefaultsWithFuelAndReserveData() {
        var aircraft = MockFlightData.aircraft();

        assertTrue(aircraft.stream().anyMatch(option -> option.id().equals("cessna-172")));
        assertTrue(aircraft.stream().anyMatch(option -> option.id().equals("piper-pa-28")));
        assertTrue(aircraft.stream().anyMatch(option -> option.id().equals("diamond-da40")));
        assertTrue(aircraft.stream().allMatch(option -> option.cruiseSpeedKmh() > 0.0));
        assertTrue(aircraft.stream().allMatch(option -> option.fuelBurnLitersPerHour() > 0.0));
        assertTrue(aircraft.stream().allMatch(option -> option.fuelType() != null && !option.fuelType().isBlank()));
        assertTrue(aircraft.stream().allMatch(option -> option.maxEnduranceHours() > 0.0));
        assertTrue(aircraft.stream().allMatch(option -> option.recommendedReserveMinutes() > 0));
    }

    @Test
    void providesMockFuelPricesForSupportedFuelTypes() {
        var fuelPrices = MockFlightData.fuelPrices();

        assertTrue(fuelPrices.stream().anyMatch(fuelPrice -> fuelPrice.fuelType().equals("AVGAS_100LL")));
        assertTrue(fuelPrices.stream().anyMatch(fuelPrice -> fuelPrice.fuelType().equals("JET_A1")));
        assertTrue(fuelPrices.stream().anyMatch(fuelPrice -> fuelPrice.fuelType().equals("MOGAS")));
        assertTrue(fuelPrices.stream().allMatch(fuelPrice -> fuelPrice.pricePerLiter() >= 0.0));
        assertTrue(fuelPrices.stream().allMatch(fuelPrice -> fuelPrice.source().name().equals("MOCK")));
    }
}
