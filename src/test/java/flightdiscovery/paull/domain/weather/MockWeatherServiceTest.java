package flightdiscovery.paull.domain.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import flightdiscovery.paull.domain.model.Airport;
import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.RouteType;

class MockWeatherServiceTest {

    private final MockWeatherService weatherService = new MockWeatherService();

    @Test
    void returnsDeterministicMockWeatherInUsableRanges() {
        FlightRoute route = new FlightRoute(
                "test-route",
                "Test route",
                "Test route",
                RouteType.PREDEFINED,
                new Airport("TEST", "Test Airport", 0.0, 0.0),
                List.of(),
                List.of("coast"),
                0.0,
                0.0,
                80.0
        );

        var firstWeather = weatherService.weatherFor(route);
        var secondWeather = weatherService.weatherFor(route);

        assertEquals(firstWeather, secondWeather);
        assertTrue(firstWeather.windKmh() >= 0.0);
        assertTrue(firstWeather.cloudCoverPercent() >= 0.0);
        assertTrue(firstWeather.cloudCoverPercent() <= 100.0);
        assertTrue(firstWeather.precipitationProbability() >= 0.0);
        assertTrue(firstWeather.precipitationProbability() <= 100.0);
        assertTrue(firstWeather.visibilityKm() >= 0.0);
        assertTrue(firstWeather.weatherScore() >= 0.0);
        assertTrue(firstWeather.weatherScore() <= 100.0);
    }
}
