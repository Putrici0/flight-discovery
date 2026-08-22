package flightdiscovery.paull.domain.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import flightdiscovery.paull.domain.mock.MockFlightData;
import flightdiscovery.paull.domain.model.VisualWaypoint;

@Repository
public class MockWaypointRepository {

    public List<VisualWaypoint> findAll() {
        return MockFlightData.visualWaypoints();
    }
}

