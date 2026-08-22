package flightdiscovery.paull.domain.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import flightdiscovery.paull.domain.mock.MockFlightData;
import flightdiscovery.paull.domain.model.FuelPrice;

@Repository
public class MockFuelPriceRepository {

    public List<FuelPrice> findAll() {
        return MockFlightData.fuelPrices();
    }

    public Optional<FuelPrice> findByFuelType(String fuelType) {
        if (fuelType == null || fuelType.isBlank()) {
            return Optional.empty();
        }

        return findAll().stream()
                .filter(fuelPrice -> fuelPrice.fuelType().equalsIgnoreCase(fuelType))
                .findFirst();
    }
}
