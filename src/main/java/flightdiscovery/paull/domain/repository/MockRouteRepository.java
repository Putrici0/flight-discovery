package flightdiscovery.paull.domain.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import flightdiscovery.paull.domain.mock.MockFlightData;
import flightdiscovery.paull.domain.model.FlightRoute;

@Repository
public class MockRouteRepository {

    public List<FlightRoute> findAll() {
        return MockFlightData.routes();
    }

    public List<FlightRoute> findByDepartureAirportCode(String departureAirportCode) {
        return findAll().stream()
                .filter(route -> route.departureAirport().code().equalsIgnoreCase(departureAirportCode))
                .toList();
    }
}

