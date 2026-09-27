package flightdiscovery.paull.application.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import flightdiscovery.paull.domain.aviationweather.AviationWeatherClient;
import flightdiscovery.paull.domain.aviationweather.AviationWeatherException;
import flightdiscovery.paull.domain.mock.MockFlightData;
import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.MetarReport;
import flightdiscovery.paull.domain.model.RouteType;
import flightdiscovery.paull.domain.repository.MockAirportRepository;

class RouteAviationWeatherServiceTest {

    @Test
    void includesDepartureAirportMetarAndHandlesMissingTaf() {
        AviationWeatherClient client = Mockito.mock(AviationWeatherClient.class);
        when(client.latestMetar("GCLP")).thenReturn(Optional.of(metar("GCLP")));
        when(client.latestTaf("GCLP")).thenReturn(Optional.empty());
        RouteAviationWeatherService service = new RouteAviationWeatherService(client, new MockAirportRepository());

        var summary = service.aviationWeatherSummary(localRoute(), MockFlightData.GCLP);

        assertEquals("AviationWeather.gov", summary.provider());
        assertFalse(summary.operationalUseAllowed());
        assertEquals("GCLP", summary.airports().getFirst().airportCode());
        assertEquals("METAR GCLP 271130Z AUTO", summary.airports().getFirst().metar().rawText());
        assertFalse(summary.airports().getFirst().tafAvailable());
        assertTrue(summary.airports().getFirst().warnings().contains("Sin TAF disponible para GCLP"));
    }

    @Test
    void networkErrorsBecomeWarningsInsteadOfBreakingSummary() {
        AviationWeatherClient client = Mockito.mock(AviationWeatherClient.class);
        when(client.latestMetar("GCLP")).thenThrow(new AviationWeatherException("boom", null));
        when(client.latestTaf("GCLP")).thenThrow(new AviationWeatherException("boom", null));
        RouteAviationWeatherService service = new RouteAviationWeatherService(client, new MockAirportRepository());

        var summary = service.aviationWeatherSummary(localRoute(), MockFlightData.GCLP);

        assertEquals(1, summary.airports().size());
        assertTrue(summary.airports().getFirst().warnings().contains("No se pudo consultar METAR para GCLP"));
        assertTrue(summary.airports().getFirst().warnings().contains("No se pudo consultar TAF para GCLP"));
        assertTrue(summary.warnings().contains("No se pudo consultar METAR para GCLP"));
    }

    private FlightRoute localRoute() {
        return new FlightRoute(
                "test-local",
                "Test local",
                "Test local",
                RouteType.PREDEFINED,
                MockFlightData.GCLP,
                List.of(),
                List.of("coast"),
                10.0,
                10.0,
                80.0
        );
    }

    private MetarReport metar(String airportCode) {
        return new MetarReport(
                airportCode,
                "Test airport",
                "METAR " + airportCode + " 271130Z AUTO",
                "2026-09-27T11:30:00Z",
                "2026-09-27T11:34:00Z",
                30L,
                "VFR",
                20,
                6,
                null,
                "6+",
                1018,
                26,
                23,
                null,
                List.of(),
                "AviationWeather.gov"
        );
    }
}
