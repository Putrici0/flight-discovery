package flightdiscovery.paull.domain.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import flightdiscovery.paull.domain.mock.MockFlightData;
import flightdiscovery.paull.domain.model.Aircraft;

@Repository
public class MockAircraftRepository {

    public List<Aircraft> findAll() {
        return MockFlightData.aircraft();
    }

    public Optional<Aircraft> findById(String aircraftId) {
        if (aircraftId == null || aircraftId.isBlank()) {
            return Optional.empty();
        }

        return findAll().stream()
                .filter(aircraft -> aircraft.id().equalsIgnoreCase(aircraftId))
                .findFirst();
    }
}

