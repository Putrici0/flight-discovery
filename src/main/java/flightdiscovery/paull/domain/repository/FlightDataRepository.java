package flightdiscovery.paull.domain.repository;

import java.util.List;
import java.util.Optional;

import flightdiscovery.paull.domain.model.Aircraft;
import flightdiscovery.paull.domain.model.Airport;
import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.VisualWaypoint;

public interface FlightDataRepository {

    List<Airport> airports();

    List<Aircraft> aircraft();

    List<FlightRoute> routes();

    List<VisualWaypoint> visualWaypoints();

    default Optional<Aircraft> findAircraftById(String aircraftId) {
        if (aircraftId == null || aircraftId.isBlank()) {
            return Optional.empty();
        }

        return aircraft().stream()
                .filter(aircraft -> aircraft.id().equalsIgnoreCase(aircraftId))
                .findFirst();
    }
}
