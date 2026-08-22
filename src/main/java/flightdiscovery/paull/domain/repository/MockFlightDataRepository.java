package flightdiscovery.paull.domain.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import flightdiscovery.paull.domain.mock.MockFlightData;
import flightdiscovery.paull.domain.model.Aircraft;
import flightdiscovery.paull.domain.model.Airport;
import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.VisualWaypoint;

@Repository
public class MockFlightDataRepository implements FlightDataRepository {

    @Override
    public List<Airport> airports() {
        return MockFlightData.airports();
    }

    @Override
    public List<Aircraft> aircraft() {
        return MockFlightData.aircraft();
    }

    @Override
    public List<FlightRoute> routes() {
        return MockFlightData.routes();
    }

    @Override
    public List<VisualWaypoint> visualWaypoints() {
        return MockFlightData.visualWaypoints();
    }
}
