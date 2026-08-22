package flightdiscovery.paull.domain.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import flightdiscovery.paull.domain.mock.MockFlightData;
import flightdiscovery.paull.domain.model.Airport;

@Repository
public class MockAirportRepository {

    public List<Airport> findAll() {
        return MockFlightData.airports();
    }

    public Optional<Airport> findByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }

        return findAll().stream()
                .filter(airport -> airport.code().equalsIgnoreCase(code))
                .findFirst();
    }
}

