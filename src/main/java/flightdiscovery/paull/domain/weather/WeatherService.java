package flightdiscovery.paull.domain.weather;

import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.WeatherData;

public interface WeatherService {

    WeatherData weatherFor(FlightRoute route);
}
