package flightdiscovery.paull.domain.weather;

import java.time.LocalDateTime;

import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.WeatherData;

public interface WeatherService {

    WeatherData weatherFor(FlightRoute route);

    default WeatherData weatherFor(FlightRoute route, LocalDateTime plannedDepartureDateTime) {
        return weatherFor(route);
    }

    WeatherData weatherFor(double latitude, double longitude, LocalDateTime plannedDepartureDateTime);
}
