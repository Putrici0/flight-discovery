package flightdiscovery.paull.domain.weather;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import flightdiscovery.paull.domain.model.Airport;
import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.RouteType;
import flightdiscovery.paull.domain.model.WeatherData;

class OpenMeteoWeatherServiceTest {

    @Test
    void mapsOpenMeteoHourlyForecastToWeatherData() {
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl("https://api.open-meteo.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        OpenMeteoWeatherService weatherService = new OpenMeteoWeatherService(
                restClientBuilder.build(),
                new MockWeatherService()
        );
        server.expect(requestTo(containsString("/v1/forecast")))
                .andExpect(queryParam("latitude", "28.0"))
                .andExpect(queryParam("longitude", "-15.0"))
                .andExpect(queryParam("start_date", "2026-08-25"))
                .andExpect(queryParam("end_date", "2026-08-25"))
                .andRespond(withSuccess("""
                        {
                          "hourly": {
                            "time": ["2026-08-25T09:00", "2026-08-25T10:00"],
                            "temperature_2m": [22.5, 23.7],
                            "wind_speed_10m": [12.0, 18.5],
                            "cloud_cover": [35.0, 42.0],
                            "precipitation_probability": [5.0, 10.0],
                            "visibility": [24000.0, 18000.0]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        WeatherData weatherData = weatherService.weatherFor(
                28.0,
                -15.0,
                LocalDateTime.parse("2026-08-25T10:20")
        );

        assertEquals(18.5, weatherData.windKmh(), 0.01);
        assertEquals(42.0, weatherData.cloudCoverPercent(), 0.01);
        assertEquals(10.0, weatherData.precipitationProbability(), 0.01);
        assertEquals(18.0, weatherData.visibilityKm(), 0.01);
        assertEquals(23.7, weatherData.temperatureCelsius(), 0.01);
        assertTrue(weatherData.weatherScore() >= 0.0);
        assertTrue(weatherData.weatherScore() <= 100.0);
        server.verify();
    }

    @Test
    void explicitCoordinateLookupThrowsControlledErrorWhenOpenMeteoFails() {
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl("https://api.open-meteo.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        OpenMeteoWeatherService weatherService = new OpenMeteoWeatherService(
                restClientBuilder.build(),
                new MockWeatherService()
        );
        server.expect(requestTo(containsString("/v1/forecast")))
                .andRespond(withServerError());

        assertThrows(WeatherServiceException.class, () -> weatherService.weatherFor(
                28.0,
                -15.0,
                LocalDateTime.parse("2026-08-25T10:00")
        ));
        server.verify();
    }

    @Test
    void routeLookupFallsBackToMockWeatherWhenOpenMeteoFails() {
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl("https://api.open-meteo.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        OpenMeteoWeatherService weatherService = new OpenMeteoWeatherService(
                restClientBuilder.build(),
                new MockWeatherService()
        );
        FlightRoute route = route();
        server.expect(requestTo(containsString("/v1/forecast")))
                .andRespond(withServerError());

        WeatherData fallbackWeatherData = weatherService.weatherFor(route, LocalDateTime.parse("2026-08-25T10:00"));

        assertEquals(new MockWeatherService().weatherFor(route), fallbackWeatherData);
        server.verify();
    }

    private FlightRoute route() {
        return new FlightRoute(
                "test-route",
                "Test route",
                "Test route",
                RouteType.PREDEFINED,
                new Airport("TEST", "Test Airport", 28.0, -15.0),
                List.of(),
                List.of("coast"),
                0.0,
                0.0,
                80.0
        );
    }
}
